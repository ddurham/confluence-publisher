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

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Converts an asciidoc folder and publishes it to Confluence, with the same settings and defaults as the command line
 * client. The publisher runs in a forked worker JVM so that AsciidoctorJ and JRuby stay out of the build's JVM.
 */
@UntrackedTask(because = "publishing changes Confluence, which Gradle cannot see")
public abstract class PublishToConfluence extends DefaultTask {

    static final String MAX_RETRIES_SYSTEM_PROPERTY = "asciidoc-confluence-publisher.maxRetriesOnTooManyRequests";

    @Input
    @Optional
    public abstract Property<String> getRootConfluenceUrl();

    @Input
    public abstract Property<Boolean> getSkipSslVerification();

    /**
     * Defaults to the environment variable {@code CONFLUENCE_PUBLISHER_USERNAME}, then the Gradle property
     * {@code confluencePublisher.username}.
     */
    @Input
    @Optional
    public abstract Property<String> getUsername();

    /**
     * The API token; defaults to the environment variable {@code CONFLUENCE_PUBLISHER_PASSWORD}.
     */
    @Internal
    public abstract Property<String> getPassword();

    /**
     * A shell command that prints the API token, for example {@code pass show confluence/token}; run when publishing
     * without a password or authorization, so the token is never stored. Defaults to the environment variable
     * {@code CONFLUENCE_PUBLISHER_PASSWORD_COMMAND}, then the Gradle property {@code confluencePublisher.passwordCommand}.
     */
    @Input
    @Optional
    public abstract Property<String> getPasswordCommand();

    /**
     * A ready-made {@code Authorization} header value, sent instead of username and password (REST API v2 only);
     * defaults to the environment variable {@code CONFLUENCE_PUBLISHER_AUTHORIZATION}.
     */
    @Internal
    public abstract Property<String> getAuthorization();

    @Input
    public abstract Property<String> getSpaceKey();

    @Input
    public abstract Property<String> getAncestorId();

    @Input
    @Optional
    public abstract Property<String> getVersionMessage();

    @Input
    @Optional
    public abstract Property<Double> getMaxRequestsPerSecond();

    @Input
    @Optional
    public abstract Property<Integer> getConnectionTimeToLive();

    /**
     * {@code APPEND_TO_ANCESTOR} (default) or {@code REPLACE_ANCESTOR}.
     */
    @Input
    public abstract Property<String> getPublishingStrategy();

    /**
     * {@code REMOVE_ORPHANS} (default) or {@code KEEP_ORPHANS}.
     */
    @Input
    public abstract Property<String> getOrphanRemovalStrategy();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getAsciidocRootFolder();

    @OutputDirectory
    public abstract DirectoryProperty getBuildFolder();

    @Input
    public abstract Property<String> getSourceEncoding();

    @Input
    @Optional
    public abstract Property<String> getPageTitlePrefix();

    @Input
    @Optional
    public abstract Property<String> getPageTitleSuffix();

    @Input
    public abstract MapProperty<String, String> getAttributes();

    @Input
    @Optional
    public abstract Property<String> getProxyScheme();

    @Input
    @Optional
    public abstract Property<String> getProxyHost();

    @Input
    @Optional
    public abstract Property<Integer> getProxyPort();

    @Input
    @Optional
    public abstract Property<String> getProxyUsername();

    @Internal
    public abstract Property<String> getProxyPassword();

    @Input
    public abstract Property<Boolean> getConvertOnly();

    @Input
    public abstract Property<Boolean> getNotifyWatchers();

    @Input
    public abstract Property<String> getRestApiVersion();

    /**
     * Where the published page tree is written as JSON, with each page's source path.
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getMetadataOutputFile();

    /**
     * With {@link #getOwnerRepo()} and {@link #getOwnerPathPrefix()} (all three or none): marks pages as owned by a
     * repo and refuses to overwrite pages of another one.
     */
    @Input
    @Optional
    public abstract Property<String> getOwnerLabel();

    @Input
    @Optional
    public abstract Property<String> getOwnerRepo();

    /**
     * An empty value means the repo root.
     */
    @Input
    @Optional
    public abstract Property<String> getOwnerPathPrefix();

    /**
     * The repo's working tree, so a page whose source moved within the repo can be taken over.
     */
    @Internal
    public abstract DirectoryProperty getOwnerRepoRoot();

    @Input
    public abstract Property<Boolean> getFailOnError();

    /**
     * How often a request answered with 429 is sent again; unset keeps the publisher's default, 0 disables.
     */
    @Input
    @Optional
    public abstract Property<Integer> getMaxRetriesOnTooManyRequests();

    /**
     * The worker JVM's maximum heap, for example {@code 1g}.
     */
    @Input
    @Optional
    public abstract Property<String> getWorkerMaxHeapSize();

    /**
     * The publisher and its dependencies, from the {@code confluencePublisher} configuration.
     */
    @Classpath
    public abstract ConfigurableFileCollection getPublisherClasspath();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ProviderFactory getProviders();

    @TaskAction
    public void publish() {
        boolean needsPassword = !getConvertOnly().get() && !getAuthorization().isPresent() && !getPassword().isPresent();
        String commandPassword = needsPassword && getPasswordCommand().isPresent() ? runPasswordCommand(getPasswordCommand().get()) : null;

        WorkQueue workQueue = getWorkerExecutor().processIsolation((spec) -> {
            spec.getClasspath().from(getPublisherClasspath());
            spec.forkOptions((forkOptions) -> {
                if (getMaxRetriesOnTooManyRequests().isPresent()) {
                    forkOptions.systemProperty(MAX_RETRIES_SYSTEM_PROPERTY, getMaxRetriesOnTooManyRequests().get());
                }

                if (getWorkerMaxHeapSize().isPresent()) {
                    forkOptions.setMaxHeapSize(getWorkerMaxHeapSize().get());
                }
            });
        });

        workQueue.submit(PublishToConfluenceAction.class, (parameters) -> {
            parameters.getRootConfluenceUrl().set(getRootConfluenceUrl());
            parameters.getSkipSslVerification().set(getSkipSslVerification());
            parameters.getUsername().set(getUsername());
            parameters.getPassword().set(commandPassword != null ? getProviders().provider(() -> commandPassword) : getPassword());
            parameters.getAuthorization().set(getAuthorization());
            parameters.getSpaceKey().set(getSpaceKey());
            parameters.getAncestorId().set(getAncestorId());
            parameters.getVersionMessage().set(getVersionMessage());
            parameters.getMaxRequestsPerSecond().set(getMaxRequestsPerSecond());
            parameters.getConnectionTimeToLive().set(getConnectionTimeToLive());
            parameters.getPublishingStrategy().set(getPublishingStrategy());
            parameters.getOrphanRemovalStrategy().set(getOrphanRemovalStrategy());
            parameters.getAsciidocRootFolder().set(getAsciidocRootFolder());
            parameters.getBuildFolder().set(getBuildFolder());
            parameters.getSourceEncoding().set(getSourceEncoding());
            parameters.getPageTitlePrefix().set(getPageTitlePrefix());
            parameters.getPageTitleSuffix().set(getPageTitleSuffix());
            parameters.getAttributes().set(getAttributes());
            parameters.getProxyScheme().set(getProxyScheme());
            parameters.getProxyHost().set(getProxyHost());
            parameters.getProxyPort().set(getProxyPort());
            parameters.getProxyUsername().set(getProxyUsername());
            parameters.getProxyPassword().set(getProxyPassword());
            parameters.getConvertOnly().set(getConvertOnly());
            parameters.getNotifyWatchers().set(getNotifyWatchers());
            parameters.getRestApiVersion().set(getRestApiVersion());
            parameters.getMetadataOutputFile().set(getMetadataOutputFile());
            parameters.getOwnerLabel().set(getOwnerLabel());
            parameters.getOwnerRepo().set(getOwnerRepo());
            parameters.getOwnerPathPrefix().set(getOwnerPathPrefix());
            parameters.getOwnerRepoRoot().set(getOwnerRepoRoot());
            parameters.getFailOnError().set(getFailOnError());
        });
    }

    private String runPasswordCommand(String command) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean windows = System.getProperty("os.name").toLowerCase().contains("windows");

        ExecResult result = getExecOperations().exec((spec) -> {
            spec.commandLine(windows ? new String[]{"cmd", "/c", command} : new String[]{"sh", "-c", command});
            spec.setStandardOutput(output);
            spec.setIgnoreExitValue(true);
        });

        if (result.getExitValue() != 0) {
            throw new GradleException("'passwordCommand' (" + command + ") exited with " + result.getExitValue());
        }

        // the first line only: `pass show` prints further lines after the secret
        String password = output.toString(Charset.defaultCharset()).lines().findFirst().orElse("").trim();
        if (password.isEmpty()) {
            throw new GradleException("'passwordCommand' (" + command + ") printed nothing");
        }

        return password;
    }

}
