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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the plugin in a real build. The publisher comes from ~/.m2 ({@code ./mvnw install} first) or Nexus; nothing
 * here talks to Confluence.
 */
public class AsciidocConfluencePublisherPluginTest {

    @TempDir
    Path projectDir;

    @BeforeEach
    public void writeProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'docs'\n");
        write("build.gradle", ""
                + "plugins {\n"
                + "    id 'org.sahli.asciidoc.confluence.publisher'\n"
                + "}\n"
                + "\n"
                // only the publisher from ~/.m2: Maven keeps no Gradle module metadata, so a library such as Guava
                // would resolve there without the variant matching that real consumers get from Nexus
                + "repositories {\n"
                + "    mavenLocal { content { includeGroup 'org.sahli.asciidoc.confluence.publisher' } }\n"
                + "    maven {\n"
                + "        url = '" + System.getProperty("nexusPublicUrl") + "'\n"
                + "        content { excludeGroup 'org.sahli.asciidoc.confluence.publisher' }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "tasks.named('publishToConfluence') {\n"
                + "    spaceKey = 'SPACE'\n"
                + "    ancestorId = '42'\n"
                + "    convertOnly = providers.gradleProperty('convertOnly').map { it.toBoolean() }.orElse(true)\n"
                + "    attributes = [product: 'Widget']\n"
                + "    metadataOutputFile = layout.buildDirectory.file('metadata.json')\n"
                + "}\n"
                + "\n"
                + "tasks.register('checkIsolation') {\n"
                + "    def pluginClassLoader = tasks.named('publishToConfluence').get().class.classLoader\n"
                + "    doLast {\n"
                + "        try {\n"
                + "            pluginClassLoader.loadClass('org.asciidoctor.Asciidoctor')\n"
                + "            println 'asciidoctor is in the build JVM'\n"
                + "        } catch (ClassNotFoundException ignored) {\n"
                + "            println 'asciidoctor is not in the build JVM'\n"
                + "        }\n"
                + "    }\n"
                + "}\n");
        write("src/docs/asciidoc/index.adoc", "= Home\n\nAbout {product}.\n");
        write("src/docs/asciidoc/index/child.adoc", "= Child\n\nA child page.\n");
    }

    @Test
    public void convertOnly_convertsPagesAndWritesMetadata() throws IOException {
        BuildResult result = runner("publishToConfluence", "--configuration-cache").build();

        assertEquals(SUCCESS, result.task(":publishToConfluence").getOutcome());
        assertTrue(result.getOutput().contains("Publishing to Confluence skipped ('convert only' is enabled)"), result.getOutput());

        String metadata = read("build/metadata.json");
        assertTrue(metadata.contains("\"title\" : \"Home\""), metadata);
        assertTrue(metadata.contains("\"title\" : \"Child\""), metadata);
        assertTrue(metadata.contains("\"sourcePath\" : \"index/child.adoc\""), metadata);

        try (Stream<Path> files = Files.walk(this.projectDir.resolve("build/asciidoc-confluence-publisher"))) {
            assertTrue(files.filter(Files::isRegularFile).anyMatch((file) -> contains(file, "About Widget.")), "converted home page with attribute");
        }
    }

    @Test
    public void convertOnly_onGradle9() {
        BuildResult result = runner("publishToConfluence", "--configuration-cache").withGradleVersion("9.4.0").build();

        assertEquals(SUCCESS, result.task(":publishToConfluence").getOutcome());
    }

    @Test
    public void configurationCache_isReused() {
        runner("publishToConfluence", "--configuration-cache").build();
        BuildResult result = runner("publishToConfluence", "--configuration-cache").build();

        assertTrue(result.getOutput().contains("Reusing configuration cache."), result.getOutput());
    }

    @Test
    public void publish_withoutUrl_fails() {
        BuildResult result = runner("publishToConfluence", "-PconvertOnly=false").buildAndFail();

        assertTrue(result.getOutput().contains("'rootConfluenceUrl' must be set"), result.getOutput());
    }

    @Test
    public void publish_withUrlButWithoutPassword_fails() throws IOException {
        appendToBuild("tasks.named('publishToConfluence') { rootConfluenceUrl = 'http://localhost:1' }\n");

        BuildResult result = runner("publishToConfluence", "-PconvertOnly=false").buildAndFail();

        assertTrue(result.getOutput().contains("'password' must be set"), result.getOutput());
    }

    @Test
    public void passwordCommand_suppliesThePassword() throws IOException {
        appendToBuild("tasks.named('publishToConfluence') { rootConfluenceUrl = 'http://localhost:1' }\n");

        BuildResult result = runner(Map.of("CONFLUENCE_PUBLISHER_USERNAME", "someone@example.com"),
                "publishToConfluence", "-PconvertOnly=false", "-PconfluencePublisher.passwordCommand=printf 'token\\nmetadata\\n'").buildAndFail();

        // past the password check, failing on the unreachable Confluence
        assertTrue(result.getOutput().contains("Publishing to Confluence (REST API v2)"), result.getOutput());
        assertFalse(result.getOutput().contains("'password' must be set"), result.getOutput());
    }

    @Test
    public void passwordCommand_failing_failsTheTask() throws IOException {
        appendToBuild("tasks.named('publishToConfluence') { rootConfluenceUrl = 'http://localhost:1' }\n");

        BuildResult result = runner(Map.of("CONFLUENCE_PUBLISHER_PASSWORD_COMMAND", "exit 3"), "publishToConfluence", "-PconvertOnly=false").buildAndFail();

        assertTrue(result.getOutput().contains("'passwordCommand' (exit 3) exited with 3"), result.getOutput());
    }

    @Test
    public void passwordCommand_isNotRunForConvertOnly() {
        BuildResult result = runner(Map.of("CONFLUENCE_PUBLISHER_PASSWORD_COMMAND", "exit 3"), "publishToConfluence").build();

        assertEquals(SUCCESS, result.task(":publishToConfluence").getOutcome());
    }

    @Test
    public void publisher_isNotLoadedIntoTheBuildJvm() {
        BuildResult result = runner("checkIsolation").build();

        assertTrue(result.getOutput().contains("asciidoctor is not in the build JVM"), result.getOutput());
    }

    private GradleRunner runner(String... arguments) {
        return runner(Map.of(), arguments);
    }

    private GradleRunner runner(Map<String, String> extraEnvironment, String... arguments) {
        // the developer's own credentials must not leak into the build under test
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.keySet().removeIf((name) -> name.startsWith("CONFLUENCE_PUBLISHER_"));
        environment.putAll(extraEnvironment);

        return GradleRunner.create()
                .withProjectDir(this.projectDir.toFile())
                .withPluginClasspath()
                .withEnvironment(environment)
                .withArguments(arguments)
                .forwardOutput();
    }

    private void appendToBuild(String content) throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), content, UTF_8, APPEND);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = this.projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, UTF_8);
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(this.projectDir.resolve(relativePath), UTF_8);
    }

    private static boolean contains(Path file, String text) {
        try {
            return Files.readString(file, UTF_8).contains(text);
        } catch (IOException e) {
            return false;
        }
    }

}
