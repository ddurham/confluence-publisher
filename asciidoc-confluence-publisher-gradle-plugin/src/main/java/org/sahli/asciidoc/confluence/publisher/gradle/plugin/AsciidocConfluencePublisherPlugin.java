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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Adds the {@code publishToConfluence} task and the {@code confluencePublisher} configuration, which defaults to the
 * publisher of this plugin's version.
 */
public class AsciidocConfluencePublisherPlugin implements Plugin<Project> {

    public static final String CONFIGURATION_NAME = "confluencePublisher";
    public static final String TASK_NAME = "publishToConfluence";

    private static final String PUBLISHER_MODULE = "org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-cli";

    @Override
    public void apply(Project project) {
        ObjectFactory objects = project.getObjects();
        Configuration publisherConfiguration = project.getConfigurations().create(CONFIGURATION_NAME, (configuration) -> {
            configuration.setDescription("The asciidoc confluence publisher run by the publishToConfluence task");
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setVisible(false);
            // a Java runtime classpath, so that libraries with variants (Guava) resolve without the java plugin
            configuration.attributes((attributes) -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
                attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
            });
            configuration.defaultDependencies((dependencies) -> dependencies.add(project.getDependencies().create(PUBLISHER_MODULE + ":" + pluginVersion())));
        });

        ProviderFactory providers = project.getProviders();

        project.getTasks().withType(PublishToConfluence.class).configureEach((task) -> {
            task.getPublisherClasspath().from(publisherConfiguration);
            task.getAsciidocRootFolder().convention(project.getLayout().getProjectDirectory().dir("src/docs/asciidoc"));
            task.getBuildFolder().convention(project.getLayout().getBuildDirectory().dir("asciidoc-confluence-publisher"));
            task.getUsername().convention(nonEmpty(providers.environmentVariable("CONFLUENCE_PUBLISHER_USERNAME"))
                    .orElse(nonEmpty(providers.gradleProperty("confluencePublisher.username"))));
            task.getPassword().convention(nonEmpty(providers.environmentVariable("CONFLUENCE_PUBLISHER_PASSWORD")));
            task.getPasswordCommand().convention(nonEmpty(providers.environmentVariable("CONFLUENCE_PUBLISHER_PASSWORD_COMMAND"))
                    .orElse(nonEmpty(providers.gradleProperty("confluencePublisher.passwordCommand"))));
            task.getAuthorization().convention(nonEmpty(providers.environmentVariable("CONFLUENCE_PUBLISHER_AUTHORIZATION")));
            task.getSkipSslVerification().convention(false);
            task.getPublishingStrategy().convention("APPEND_TO_ANCESTOR");
            task.getOrphanRemovalStrategy().convention("REMOVE_ORPHANS");
            task.getSourceEncoding().convention("UTF-8");
            task.getConvertOnly().convention(false);
            task.getNotifyWatchers().convention(true);
            task.getRestApiVersion().convention("v2");
            task.getFailOnError().convention(true);
        });

        project.getTasks().register(TASK_NAME, PublishToConfluence.class, (task) -> {
            task.setGroup("documentation");
            task.setDescription("Publishes the asciidoc documentation to Confluence");
        });
    }

    private static Provider<String> nonEmpty(Provider<String> provider) {
        return provider.filter((value) -> !value.isEmpty());
    }

    private static String pluginVersion() {
        try (InputStream inputStream = AsciidocConfluencePublisherPlugin.class.getResourceAsStream("/asciidoc-confluence-publisher-gradle-plugin.properties")) {
            if (inputStream == null) {
                throw new GradleException("asciidoc-confluence-publisher-gradle-plugin.properties is missing from the plugin jar");
            }

            Properties properties = new Properties();
            properties.load(inputStream);

            return properties.getProperty("version");
        } catch (IOException e) {
            throw new GradleException("failed to read the plugin's version", e);
        }
    }

}
