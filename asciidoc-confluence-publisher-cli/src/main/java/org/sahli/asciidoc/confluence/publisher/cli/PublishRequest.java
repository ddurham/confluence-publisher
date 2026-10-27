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

import org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy;
import org.sahli.asciidoc.confluence.publisher.client.Ownership;
import org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy.REMOVE_ORPHANS;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.APPEND_TO_ANCESTOR;

/**
 * Everything one {@link PublishRunner#run(PublishRequest)} needs, filled by the command line client and the Gradle
 * plugin. Defaults match the command line client's.
 */
public class PublishRequest {

    String rootConfluenceUrl;
    boolean skipSslVerification;
    String username;
    String password;
    String authorization;
    String spaceKey;
    String ancestorId;
    String versionMessage;
    Double maxRequestsPerSecond;
    Integer connectionTimeToLive;
    PublishingStrategy publishingStrategy = APPEND_TO_ANCESTOR;
    OrphanRemovalStrategy orphanRemovalStrategy = REMOVE_ORPHANS;
    Path asciidocRootFolder;
    Path buildFolder;
    Charset sourceEncoding = StandardCharsets.UTF_8;
    String pageTitlePrefix;
    String pageTitleSuffix;
    Map<String, Object> attributes = Collections.emptyMap();
    String proxyScheme;
    String proxyHost;
    Integer proxyPort;
    String proxyUsername;
    String proxyPassword;
    boolean convertOnly;
    boolean notifyWatchers = true;
    String restApiVersion = "v2";
    Path metadataOutputFile;
    Ownership ownership;
    boolean failOnError = true;

    public PublishRequest rootConfluenceUrl(String rootConfluenceUrl) {
        this.rootConfluenceUrl = rootConfluenceUrl;
        return this;
    }

    public PublishRequest skipSslVerification(boolean skipSslVerification) {
        this.skipSslVerification = skipSslVerification;
        return this;
    }

    public PublishRequest username(String username) {
        this.username = username;
        return this;
    }

    public PublishRequest password(String password) {
        this.password = password;
        return this;
    }

    /**
     * A ready-made {@code Authorization} header value; replaces username and password (REST API v2 only).
     */
    public PublishRequest authorization(String authorization) {
        this.authorization = authorization;
        return this;
    }

    public PublishRequest spaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
        return this;
    }

    public PublishRequest ancestorId(String ancestorId) {
        this.ancestorId = ancestorId;
        return this;
    }

    public PublishRequest versionMessage(String versionMessage) {
        this.versionMessage = versionMessage;
        return this;
    }

    public PublishRequest maxRequestsPerSecond(Double maxRequestsPerSecond) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        return this;
    }

    public PublishRequest connectionTimeToLive(Integer connectionTimeToLive) {
        this.connectionTimeToLive = connectionTimeToLive;
        return this;
    }

    public PublishRequest publishingStrategy(PublishingStrategy publishingStrategy) {
        this.publishingStrategy = publishingStrategy;
        return this;
    }

    public PublishRequest orphanRemovalStrategy(OrphanRemovalStrategy orphanRemovalStrategy) {
        this.orphanRemovalStrategy = orphanRemovalStrategy;
        return this;
    }

    public PublishRequest asciidocRootFolder(Path asciidocRootFolder) {
        this.asciidocRootFolder = asciidocRootFolder;
        return this;
    }

    /**
     * Where converted pages are written; {@code null} means a temporary folder, deleted afterwards.
     */
    public PublishRequest buildFolder(Path buildFolder) {
        this.buildFolder = buildFolder;
        return this;
    }

    public PublishRequest sourceEncoding(Charset sourceEncoding) {
        this.sourceEncoding = sourceEncoding;
        return this;
    }

    public PublishRequest pageTitlePrefix(String pageTitlePrefix) {
        this.pageTitlePrefix = pageTitlePrefix;
        return this;
    }

    public PublishRequest pageTitleSuffix(String pageTitleSuffix) {
        this.pageTitleSuffix = pageTitleSuffix;
        return this;
    }

    public PublishRequest attributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }

    public PublishRequest proxyScheme(String proxyScheme) {
        this.proxyScheme = proxyScheme;
        return this;
    }

    public PublishRequest proxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    public PublishRequest proxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
        return this;
    }

    public PublishRequest proxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
        return this;
    }

    public PublishRequest proxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
        return this;
    }

    public PublishRequest convertOnly(boolean convertOnly) {
        this.convertOnly = convertOnly;
        return this;
    }

    public PublishRequest notifyWatchers(boolean notifyWatchers) {
        this.notifyWatchers = notifyWatchers;
        return this;
    }

    public PublishRequest restApiVersion(String restApiVersion) {
        this.restApiVersion = restApiVersion;
        return this;
    }

    public PublishRequest metadataOutputFile(Path metadataOutputFile) {
        this.metadataOutputFile = metadataOutputFile;
        return this;
    }

    public PublishRequest ownership(Ownership ownership) {
        this.ownership = ownership;
        return this;
    }

    public PublishRequest failOnError(boolean failOnError) {
        this.failOnError = failOnError;
        return this;
    }

}
