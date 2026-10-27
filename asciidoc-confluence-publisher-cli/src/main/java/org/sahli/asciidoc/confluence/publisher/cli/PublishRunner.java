/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.cli;

import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisherListener;
import org.sahli.asciidoc.confluence.publisher.client.Ownership;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestV1Client;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestV2Client;
import org.sahli.asciidoc.confluence.publisher.client.http.ProxyConfiguration;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluenceConverter;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.FolderBasedAsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.PageTitlePostProcessor;
import org.sahli.asciidoc.confluence.publisher.converter.PrefixAndSuffixPageTitlePostProcessor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.walkFileTree;

/**
 * Converts an asciidoc folder and publishes it to Confluence; shared by the command line client and the Gradle plugin.
 */
public final class PublishRunner {

    private PublishRunner() {
    }

    public static void run(PublishRequest request) throws Exception {
        boolean cleanupBuildFolder = false;
        Path buildFolder = request.buildFolder;

        if (buildFolder == null) {
            buildFolder = createTempDirectory("confluence-publisher");
            cleanupBuildFolder = true;
        }

        try {
            AsciidocPagesStructureProvider asciidocPagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(request.asciidocRootFolder, request.sourceEncoding);
            PageTitlePostProcessor pageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(request.pageTitlePrefix, request.pageTitleSuffix);

            AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter(request.spaceKey, request.ancestorId);
            ConfluencePublisherMetadata confluencePublisherMetadata = asciidocConfluenceConverter.convert(asciidocPagesStructureProvider, pageTitlePostProcessor, buildFolder, request.attributes);

            if (request.metadataOutputFile != null) {
                MetadataJsonWriter.write(confluencePublisherMetadata, request.metadataOutputFile);
            }

            if (request.convertOnly) {
                System.out.println("Publishing to Confluence skipped ('convert only' is enabled)");
            } else {
                System.out.println("Publishing to Confluence (REST API " + request.restApiVersion + ")");
                ProxyConfiguration proxyConfiguration = new ProxyConfiguration(request.proxyScheme, request.proxyHost, request.proxyPort, request.proxyUsername, request.proxyPassword);

                ConfluenceClient confluenceClient = buildConfluenceClient(request.restApiVersion, request.rootConfluenceUrl, proxyConfiguration, request.skipSslVerification, request.maxRequestsPerSecond, request.connectionTimeToLive, request.username, request.password, request.authorization);
                ConfluencePublisher confluencePublisher = new ConfluencePublisher(confluencePublisherMetadata, request.publishingStrategy, request.orphanRemovalStrategy, confluenceClient, new SystemOutLoggingConfluencePublisherListener(), request.versionMessage, request.notifyWatchers, request.ownership);
                confluencePublisher.publish();
            }
        } catch (Exception e) {
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            System.err.println("Publishing to Confluence failed: " + rootCause.getMessage());

            if (request.failOnError) {
                throw e;
            } else {
                System.err.println("Publishing to Confluence failed with error: " + e.getMessage());
            }
        } finally {
            if (cleanupBuildFolder) {
                deleteDirectory(buildFolder);
            }
        }
    }

    /**
     * Builds the ownership from its parts, each {@code null} when not given; {@code null} when none of them are.
     * An empty {@code pathPrefix} means the docs live at the repo root.
     */
    public static Ownership ownership(String label, String repo, String pathPrefix, Path repoRoot) {
        if (label == null && repo == null && pathPrefix == null) {
            if (repoRoot != null) {
                throw new IllegalArgumentException("argument 'ownerRepoRoot' needs 'ownerLabel', 'ownerRepo' and 'ownerPathPrefix'");
            }

            return null;
        }

        if (label == null || repo == null || pathPrefix == null) {
            throw new IllegalArgumentException("arguments 'ownerLabel', 'ownerRepo' and 'ownerPathPrefix' must be given together or not at all");
        }

        return new Ownership(label, repo, pathPrefix, repoRoot);
    }

    static ConfluenceClient buildConfluenceClient(String restApiVersion, String rootConfluenceUrl, ProxyConfiguration proxyConfiguration, boolean skipSslVerification, Double maxRequestsPerSecond, Integer connectionTTL, String username, String password, String authorization) {
        if (authorization != null) {
            if ("v1".equals(restApiVersion)) {
                throw new IllegalArgumentException("CONFLUENCE_PUBLISHER_AUTHORIZATION needs restApiVersion v2");
            }

            return ConfluenceRestV2Client.withAuthorizationHeader(rootConfluenceUrl, proxyConfiguration, skipSslVerification, false, maxRequestsPerSecond, connectionTTL, authorization);
        }

        if ("v1".equals(restApiVersion)) {
            return new ConfluenceRestV1Client(rootConfluenceUrl, proxyConfiguration, skipSslVerification, false, maxRequestsPerSecond, connectionTTL, username, password);
        } else {
            return new ConfluenceRestV2Client(rootConfluenceUrl, proxyConfiguration, skipSslVerification, false, maxRequestsPerSecond, connectionTTL, username, password);
        }
    }

    private static void deleteDirectory(Path buildFolder) throws IOException {
        walkFileTree(buildFolder, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                delete(path);

                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                delete(path);

                return CONTINUE;
            }

        });
    }


    private static class SystemOutLoggingConfluencePublisherListener implements ConfluencePublisherListener {

        @Override
        public void pageAdded(ConfluencePage addedPage) {
            System.out.println("Added page '" + addedPage.getTitle() + "' (id " + addedPage.getContentId() + ")");
        }

        @Override
        public void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage) {
            System.out.println("Updated page '" + updatedPage.getTitle() + "' (id " + updatedPage.getContentId() + ", version " + existingPage.getVersion() + " -> " + updatedPage.getVersion() + ")");
        }

        @Override
        public void pageDeleted(ConfluencePage deletedPage) {
            System.out.println("Deleted page '" + deletedPage.getTitle() + "' (id " + deletedPage.getContentId() + ")");
        }

        @Override
        public void attachmentAdded(String attachmentFileName, String contentId) {
            System.out.println("Added attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void attachmentUpdated(String attachmentFileName, String contentId) {
            System.out.println("Updated attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void attachmentDeleted(String attachmentFileName, String contentId) {
            System.out.println("Deleted attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void publishCompleted() {
            System.out.println("Documentation successfully published to Confluence");
        }

    }

}
