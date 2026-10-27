/*
 * Copyright 2018 the original author or authors.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy.REMOVE_ORPHANS;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.APPEND_TO_ANCESTOR;

public class AsciidocConfluencePublisherCommandLineClient {

    private static final String PASSWORD_ENV_VARIABLE = "CONFLUENCE_PUBLISHER_PASSWORD";
    private static final String AUTHORIZATION_ENV_VARIABLE = "CONFLUENCE_PUBLISHER_AUTHORIZATION";

    public static void main(String[] args) throws Exception {
        ArgumentsParser argumentsParser = new ArgumentsParser();
        String rootConfluenceUrl = argumentsParser.mandatoryArgument("rootConfluenceUrl", args);
        boolean skipSslVerification = argumentsParser.optionalBooleanArgument("skipSslVerification", args).orElse(false);
        String username = argumentsParser.optionalArgument("username", args).orElse(null);
        // a ready-made Authorization header value replaces username and password
        String authorization = resolveAuthorization(System.getenv());
        String password = authorization != null ? null : resolvePassword(args, System.getenv());
        String spaceKey = argumentsParser.mandatoryArgument("spaceKey", args);
        String ancestorId = argumentsParser.mandatoryArgument("ancestorId", args);
        String versionMessage = argumentsParser.optionalArgument("versionMessage", args).orElse(null);
        Double maxRequestsPerSecond = argumentsParser.optionalArgument("maxRequestsPerSecond", args).map((value) -> parseDouble(value)).orElse(null);
        Integer connectionTTL = argumentsParser.optionalArgument("connectionTimeToLive", args).map(value -> parseInt(value)).orElse(null);
        PublishingStrategy publishingStrategy = PublishingStrategy.valueOf(argumentsParser.optionalArgument("publishingStrategy", args).orElse(APPEND_TO_ANCESTOR.name()));
        OrphanRemovalStrategy orphanRemovalStrategy = OrphanRemovalStrategy.valueOf(argumentsParser.optionalArgument("orphanRemovalStrategy", args).orElse(REMOVE_ORPHANS.name()));

        Path documentationRootFolder = Paths.get(argumentsParser.mandatoryArgument("asciidocRootFolder", args));

        Path buildFolder = argumentsParser.optionalArgument("asciidocBuildFolder", args)
                .map(Paths::get)
                .orElse(null);

        Charset sourceEncoding = Charset.forName(argumentsParser.optionalArgument("sourceEncoding", args).orElse("UTF-8"));
        String prefix = argumentsParser.optionalArgument("pageTitlePrefix", args).orElse(null);
        String suffix = argumentsParser.optionalArgument("pageTitleSuffix", args).orElse(null);
        Map<String, Object> attributes = argumentsParser.optionalJsonArgument("attributes", args).orElseGet(Collections::emptyMap);

        String proxyScheme = argumentsParser.optionalArgument("proxyScheme", args).orElse(null);
        String proxyHost = argumentsParser.optionalArgument("proxyHost", args).orElse(null);
        Integer proxyPort = argumentsParser.optionalArgument("proxyPort", args).map((value) -> parseInt(value)).orElse(null);
        String proxyUsername = argumentsParser.optionalArgument("proxyUsername", args).orElse(null);
        String proxyPassword = argumentsParser.optionalArgument("proxyPassword", args).orElse(null);

        boolean convertOnly = argumentsParser.optionalBooleanArgument("convertOnly", args).orElse(false);
        boolean notifyWatchers = argumentsParser.optionalBooleanArgument("notifyWatchers", args).orElse(true);
        String restApiVersion = argumentsParser.optionalArgument("restApiVersion", args).orElse("v2");
        Path metadataOutputFile = argumentsParser.optionalArgument("metadataOutputFile", args).map(Paths::get).orElse(null);
        Ownership ownership = resolveOwnership(args);
        boolean failOnError = argumentsParser.optionalBooleanArgument("failOnError", args).orElse(true);

        PublishRunner.run(new PublishRequest()
                .rootConfluenceUrl(rootConfluenceUrl)
                .skipSslVerification(skipSslVerification)
                .username(username)
                .password(password)
                .authorization(authorization)
                .spaceKey(spaceKey)
                .ancestorId(ancestorId)
                .versionMessage(versionMessage)
                .maxRequestsPerSecond(maxRequestsPerSecond)
                .connectionTimeToLive(connectionTTL)
                .publishingStrategy(publishingStrategy)
                .orphanRemovalStrategy(orphanRemovalStrategy)
                .asciidocRootFolder(documentationRootFolder)
                .buildFolder(buildFolder)
                .sourceEncoding(sourceEncoding)
                .pageTitlePrefix(prefix)
                .pageTitleSuffix(suffix)
                .attributes(attributes)
                .proxyScheme(proxyScheme)
                .proxyHost(proxyHost)
                .proxyPort(proxyPort)
                .proxyUsername(proxyUsername)
                .proxyPassword(proxyPassword)
                .convertOnly(convertOnly)
                .notifyWatchers(notifyWatchers)
                .restApiVersion(restApiVersion)
                .metadataOutputFile(metadataOutputFile)
                .ownership(ownership)
                .failOnError(failOnError));
    }

    static String resolvePassword(String[] args, Map<String, String> env) {
        return new ArgumentsParser().optionalArgument("password", args)
                .or(() -> Optional.ofNullable(env.get(PASSWORD_ENV_VARIABLE)).filter((value) -> !value.isEmpty()))
                .orElseThrow(() -> new IllegalArgumentException("mandatory argument 'password' is missing"));
    }

    static String resolveAuthorization(Map<String, String> env) {
        return Optional.ofNullable(env.get(AUTHORIZATION_ENV_VARIABLE)).filter((value) -> !value.isEmpty()).orElse(null);
    }

    static Ownership resolveOwnership(String[] args) {
        ArgumentsParser argumentsParser = new ArgumentsParser();
        Optional<String> label = argumentsParser.optionalArgument("ownerLabel", args);
        Optional<String> repo = argumentsParser.optionalArgument("ownerRepo", args);
        // the key's presence counts: `ownerPathPrefix=` means the docs live at the repo root
        Optional<String> pathPrefix = Arrays.stream(args)
                .filter((arg) -> arg.startsWith("ownerPathPrefix="))
                .map((arg) -> arg.substring(arg.indexOf('=') + 1))
                .findFirst();

        Optional<Path> repoRoot = argumentsParser.optionalArgument("ownerRepoRoot", args).map(Paths::get);

        return PublishRunner.ownership(label.orElse(null), repo.orElse(null), pathPrefix.orElse(null), repoRoot.orElse(null));
    }

}
