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

package org.sahli.asciidoc.confluence.publisher.gradle.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.workers.WorkAction;
import org.sahli.asciidoc.confluence.publisher.cli.PublishRequest;
import org.sahli.asciidoc.confluence.publisher.cli.PublishRunner;
import org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy;
import org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;

/**
 * Runs in the forked worker, whose classpath holds the publisher; nothing of it is loaded into the build's JVM.
 */
public abstract class PublishToConfluenceAction implements WorkAction<PublishToConfluenceParameters> {

    @Override
    public void execute() {
        PublishToConfluenceParameters parameters = getParameters();
        boolean convertOnly = parameters.getConvertOnly().get();
        String authorization = parameters.getAuthorization().getOrNull();

        if (!convertOnly) {
            if (!parameters.getRootConfluenceUrl().isPresent()) {
                throw new GradleException("'rootConfluenceUrl' must be set, unless 'convertOnly' is enabled");
            }

            if (authorization == null && !parameters.getPassword().isPresent()) {
                throw new GradleException("'password' must be set (or CONFLUENCE_PUBLISHER_PASSWORD, or CONFLUENCE_PUBLISHER_AUTHORIZATION), unless 'convertOnly' is enabled");
            }
        }

        PublishRequest request = new PublishRequest()
                .rootConfluenceUrl(parameters.getRootConfluenceUrl().getOrNull())
                .skipSslVerification(parameters.getSkipSslVerification().get())
                .username(parameters.getUsername().getOrNull())
                // a ready-made Authorization header value replaces username and password
                .password(authorization != null ? null : parameters.getPassword().getOrNull())
                .authorization(authorization)
                .spaceKey(parameters.getSpaceKey().get())
                .ancestorId(parameters.getAncestorId().get())
                .versionMessage(parameters.getVersionMessage().getOrNull())
                .maxRequestsPerSecond(parameters.getMaxRequestsPerSecond().getOrNull())
                .connectionTimeToLive(parameters.getConnectionTimeToLive().getOrNull())
                .publishingStrategy(PublishingStrategy.valueOf(parameters.getPublishingStrategy().get()))
                .orphanRemovalStrategy(OrphanRemovalStrategy.valueOf(parameters.getOrphanRemovalStrategy().get()))
                .asciidocRootFolder(path(parameters.getAsciidocRootFolder().get()))
                .buildFolder(path(parameters.getBuildFolder().get()))
                .sourceEncoding(Charset.forName(parameters.getSourceEncoding().get()))
                .pageTitlePrefix(parameters.getPageTitlePrefix().getOrNull())
                .pageTitleSuffix(parameters.getPageTitleSuffix().getOrNull())
                .attributes(new HashMap<>(parameters.getAttributes().get()))
                .proxyScheme(parameters.getProxyScheme().getOrNull())
                .proxyHost(parameters.getProxyHost().getOrNull())
                .proxyPort(parameters.getProxyPort().getOrNull())
                .proxyUsername(parameters.getProxyUsername().getOrNull())
                .proxyPassword(parameters.getProxyPassword().getOrNull())
                .convertOnly(convertOnly)
                .notifyWatchers(parameters.getNotifyWatchers().get())
                .restApiVersion(parameters.getRestApiVersion().get())
                .metadataOutputFile(parameters.getMetadataOutputFile().isPresent() ? path(parameters.getMetadataOutputFile().get()) : null)
                .ownership(PublishRunner.ownership(
                        parameters.getOwnerLabel().getOrNull(),
                        parameters.getOwnerRepo().getOrNull(),
                        parameters.getOwnerPathPrefix().getOrNull(),
                        parameters.getOwnerRepoRoot().isPresent() ? path(parameters.getOwnerRepoRoot().get()) : null))
                .failOnError(parameters.getFailOnError().get());

        try {
            PublishRunner.run(request);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("Publishing to Confluence failed", e);
        }
    }

    private static Path path(Directory directory) {
        return directory.getAsFile().toPath();
    }

    private static Path path(RegularFile file) {
        return file.getAsFile().toPath();
    }

}
