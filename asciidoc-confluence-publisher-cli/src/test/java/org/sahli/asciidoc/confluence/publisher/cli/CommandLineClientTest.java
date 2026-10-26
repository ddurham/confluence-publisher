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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;

import java.nio.file.Path;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CommandLineClientTest {

    private static final String ENV_NAME = "CONFLUENCE_PUBLISHER_PASSWORD";

    @Test
    public void resolvePassword_prefersArgument() {
        String password = AsciidocConfluencePublisherCommandLineClient.resolvePassword(new String[]{"password=fromArg"}, Map.of(ENV_NAME, "fromEnv"));

        assertThat(password, is("fromArg"));
    }

    @Test
    public void resolvePassword_fallsBackToEnv() {
        String password = AsciidocConfluencePublisherCommandLineClient.resolvePassword(new String[]{"spaceKey=X"}, Map.of(ENV_NAME, "fromEnv"));

        assertThat(password, is("fromEnv"));
    }

    @Test
    public void resolvePassword_missing_throws() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AsciidocConfluencePublisherCommandLineClient.resolvePassword(new String[]{"spaceKey=X"}, emptyMap()));

        assertThat(e.getMessage(), is("mandatory argument 'password' is missing"));
    }

    @Test
    public void metadataJsonWriter_writesTitlesSourcePathsAndChildren(@TempDir Path tempDir) throws Exception {
        ConfluencePageMetadata child = new ConfluencePageMetadata();
        child.setTitle("Child");
        child.setSourcePath("index/child.adoc");
        child.setContentFilePath("/build/child.html");

        ConfluencePageMetadata root = new ConfluencePageMetadata();
        root.setTitle("Root");
        root.setSourcePath("index.adoc");
        root.setContentFilePath("/build/index.html");
        root.setChildren(singletonList(child));

        ConfluencePublisherMetadata metadata = new ConfluencePublisherMetadata();
        metadata.setSpaceKey("SPACE");
        metadata.setAncestorId("42");
        metadata.setPages(singletonList(root));

        Path output = tempDir.resolve("metadata.json");
        MetadataJsonWriter.write(metadata, output);

        JsonNode json = new ObjectMapper().readTree(output.toFile());
        assertThat(json.get("spaceKey").asText(), is("SPACE"));
        assertThat(json.get("ancestorId").asText(), is("42"));
        assertThat(json.get("pages").get(0).get("title").asText(), is("Root"));
        assertThat(json.get("pages").get(0).get("sourcePath").asText(), is("index.adoc"));
        assertThat(json.get("pages").get(0).get("children").get(0).get("title").asText(), is("Child"));
        assertThat(json.get("pages").get(0).get("children").get(0).get("sourcePath").asText(), is("index/child.adoc"));
        assertThat(json.get("pages").get(0).has("contentFilePath"), is(false));
        assertThat(json.get("pages").get(0).get("children").get(0).has("contentFilePath"), is(false));
    }

}
