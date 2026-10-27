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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

/**
 * What {@link PublishToConfluence} hands to the forked worker; see the task for each value's meaning.
 */
public interface PublishToConfluenceParameters extends WorkParameters {

    Property<String> getRootConfluenceUrl();

    Property<Boolean> getSkipSslVerification();

    Property<String> getUsername();

    Property<String> getPassword();

    Property<String> getAuthorization();

    Property<String> getSpaceKey();

    Property<String> getAncestorId();

    Property<String> getVersionMessage();

    Property<Double> getMaxRequestsPerSecond();

    Property<Integer> getConnectionTimeToLive();

    Property<String> getPublishingStrategy();

    Property<String> getOrphanRemovalStrategy();

    DirectoryProperty getAsciidocRootFolder();

    DirectoryProperty getBuildFolder();

    Property<String> getSourceEncoding();

    Property<String> getPageTitlePrefix();

    Property<String> getPageTitleSuffix();

    MapProperty<String, String> getAttributes();

    Property<String> getProxyScheme();

    Property<String> getProxyHost();

    Property<Integer> getProxyPort();

    Property<String> getProxyUsername();

    Property<String> getProxyPassword();

    Property<Boolean> getConvertOnly();

    Property<Boolean> getNotifyWatchers();

    Property<String> getRestApiVersion();

    RegularFileProperty getMetadataOutputFile();

    Property<String> getOwnerLabel();

    Property<String> getOwnerRepo();

    Property<String> getOwnerPathPrefix();

    DirectoryProperty getOwnerRepoRoot();

    Property<Boolean> getFailOnError();

}
