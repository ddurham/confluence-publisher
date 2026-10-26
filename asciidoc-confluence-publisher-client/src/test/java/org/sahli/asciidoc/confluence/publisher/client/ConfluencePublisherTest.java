/*
 * Copyright 2016-2019 the original author or authors.
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

package org.sahli.asciidoc.confluence.publisher.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceAttachment;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher.COMBINED_HASH_PROPERTY_KEY;
import static org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher.CONTENT_HASH_PROPERTY_KEY;
import static org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher.INITIAL_PAGE_VERSION;
import static org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy.KEEP_ORPHANS;
import static org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy.REMOVE_ORPHANS;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.APPEND_TO_ANCESTOR;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.REPLACE_ANCESTOR;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.inputStreamAsString;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluencePublisherTest {

    private static final String TEST_RESOURCES = "src/test/resources/org/sahli/asciidoc/confluence/publisher/client";
    private static final String SOME_CONFLUENCE_CONTENT_SHA256_HASH = "7a901829ba6a0b6f7f084ae4313bdb5d83bc2c4ea21b452ba7073c0b0c60faae";
    private static final String ATTACHMENT_ONE_SHA256_HASH = "453fa59242a008e25053ba7c26d077395103a5747bce172e27732df666bf8230-attachment-hash";
    private static final String ATTACHMENT_TWO_SHA256_HASH = "1588264fcf12a7ad8a542728267796e77d1723d6fd6d921c63721f77e221cf07-attachment-hash";
    private static final String ATTACHMENT_THREE_SHA256_HASH = "50f35a53d86eeebca6657a096b60fced71afba6e4d9859027ac7eec0f3d97c51-attachment-hash";

    @Test
    public void publish_withMetadataMissingSpaceKey_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
            ConfluencePublisher confluencePublisher = confluencePublisher("without-space-key", confluenceClientMock);
            confluencePublisher.publish();
        });
    }

    @Test
    public void publish_withMetadataMissingAncestorId_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
            ConfluencePublisher confluencePublisher = confluencePublisher("without-ancestor-id", confluenceClientMock);
            confluencePublisher.publish();
        });
    }

    @Test
    public void publish_oneNewPageWithAncestorId_delegatesToConfluenceRestClient() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("2345");

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", confluenceClientMock, confluencePublisherListenerMock, "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_multiplePageWithAncestorId_delegatesToConfluenceRestClient() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("2345", "3456");

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("multiple-page-ancestor-id", confluenceClientMock, confluencePublisherListenerMock, "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluenceClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("72189173"), eq("Some Other Confluence Content"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("3456", "Some Other Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_exceptionWhenPublishingPage_throwsExceptionWithPageTitle() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("expected"));
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("2345", "3456");

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", confluenceClientMock, confluencePublisherListenerMock, "version message");

        // act + assert
        Exception exception = assertThrows(RuntimeException.class, () -> confluencePublisher.publish());
        assertThat(exception.getMessage(), is("Could not publish page 'Some Confluence Content'"));
        assertThat(exception.getCause().getMessage(), is("expected"));
    }

    @Test
    public void publish_multipleRootPageAndReplaceAncestorPublishingStrategy_throwsException() {
        // act
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfluencePublisher confluencePublisher = confluencePublisher("multiple-page-ancestor-id", REPLACE_ANCESTOR, "version message");
            confluencePublisher.publish();
        });

        // assert
        assertThat(exception.getMessage(), is("Multiple root pages found ('Some Confluence Content', 'Some Other Confluence Content'), but 'REPLACE_ANCESTOR' publishing strategy only supports one single root page"));
    }

    @Test
    public void publish_noRootPageAndReplaceAncestorPublishingStrategy_throwsException() {
        // act
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfluencePublisher confluencePublisher = confluencePublisher("zero-page", REPLACE_ANCESTOR, "version message");
            confluencePublisher.publish();
        });

        // assert
        assertThat(exception.getMessage(), is("No root page found, but 'REPLACE_ANCESTOR' publishing strategy requires one single root page"));
    }

    @Test
    public void publish_multiplePagesInHierarchyWithAncestorIdAsRoot_delegatesToConfluenceRestClient() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("1234", "2345");
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-multiple-pages", confluenceClientMock, confluencePublisherListenerMock, "version message");

        // act
        confluencePublisher.publish();

        // assert
        ArgumentCaptor<String> spaceKeyArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ancestorIdArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(confluenceClientMock, times(2)).addPageUnderAncestor(spaceKeyArgumentCaptor.capture(), ancestorIdArgumentCaptor.capture(), titleArgumentCaptor.capture(), contentArgumentCaptor.capture(), messageArgumentCaptor.capture());
        assertThat(spaceKeyArgumentCaptor.getAllValues(), contains("~personalSpace", "~personalSpace"));
        assertThat(ancestorIdArgumentCaptor.getAllValues(), contains("72189173", "1234"));
        assertThat(titleArgumentCaptor.getAllValues(), contains("Some Confluence Content", "Some Child Content"));
        assertThat(contentArgumentCaptor.getAllValues(), contains("<h1>Some Confluence Content</h1>", "<h1>Some Child Content</h1>"));
        assertThat(messageArgumentCaptor.getAllValues(), contains("version message", "version message"));

        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("1234", "Some Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("2345", "Some Child Content", "<h1>Some Child Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataOnePageWithNewAttachmentsAndAncestorIdAsRoot_attachesAttachmentToContent() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("4321");
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceClientMock.getAttachmentByFileName(anyString(), anyString())).thenThrow(new NotFoundException());

        ArgumentCaptor<String> contentId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attachmentFileName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InputStream> attachmentContent = ArgumentCaptor.forClass(InputStream.class);

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", confluenceClientMock, confluencePublisherListenerMock, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock).addPageUnderAncestor("~personalSpace", "72189173", "Some Confluence Content", "<h1>Some Confluence Content</h1>", null);
        verify(confluenceClientMock, times(2)).addAttachment(contentId.capture(), attachmentFileName.capture(), attachmentContent.capture());
        assertThat(contentId.getAllValues(), contains("4321", "4321"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(attachmentFileName.getAllValues().indexOf("attachmentOne.txt")), UTF_8), is("attachment1"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(attachmentFileName.getAllValues().indexOf("attachmentTwo.txt")), UTF_8), is("attachment2"));
        verify(confluenceClientMock).setPropertyByKey("4321", ATTACHMENT_ONE_SHA256_HASH, sha256Hex("attachment1"));
        verify(confluenceClientMock).setPropertyByKey("4321", ATTACHMENT_TWO_SHA256_HASH, sha256Hex("attachment2"));

        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("4321", "Some Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).attachmentAdded(eq("attachmentOne.txt"), eq("4321"));
        verify(confluencePublisherListenerMock, times(1)).attachmentAdded(eq("attachmentTwo.txt"), eq("4321"));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithExistingPageWithDifferentContentUnderRootAncestor_sendsUpdateRequest() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("3456", "Existing Page", "<h1>Some Other Confluence Content</h1>", 1);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle("~personalSpace", "1234", "Existing Page")).thenReturn("3456");
        when(confluenceClientMock.getPageWithContentAndVersionById("3456")).thenReturn(existingPage);
        when(confluenceClientMock.getPropertyByKey("3456", CONTENT_HASH_PROPERTY_KEY)).thenReturn("someWrongHash");

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id", confluenceClientMock, confluencePublisherListenerMock, "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, never()).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluenceClientMock, times(1)).updatePage(eq("3456"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2), eq("version message"), eq(true));

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(existingPage), eq(new ConfluencePage("3456", "Existing Page", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithExistingPageWithDifferentContentUnderRootAncestorAndReplaceAncestorStrategy_sendsUpdateRequest() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("1234", "Existing Page", "<h1>Some Other Confluence Content</h1>", 1);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("1234")).thenReturn(existingPage);
        when(confluenceClientMock.getPropertyByKey("1234", CONTENT_HASH_PROPERTY_KEY)).thenReturn("someWrongHash");

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id", REPLACE_ANCESTOR, confluenceClientMock, confluencePublisherListenerMock, "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, never()).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluenceClientMock, times(1)).updatePage(eq("1234"), eq(null), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2), eq("version message"), eq(true));

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(existingPage), eq(new ConfluencePage("1234", "Existing Page", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithExistingPageWithSameContentButDifferentTitleAndReplaceAncestorStrategy_sendsUpdateRequest() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("1234", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("1234")).thenReturn(existingPage);
        when(confluenceClientMock.getPropertyByKey("1234", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id", REPLACE_ANCESTOR, confluenceClientMock, confluencePublisherListenerMock, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, never()).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(null));
        verify(confluenceClientMock, times(1)).updatePage(eq("1234"), eq(null), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2), eq(null), eq(true));

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(existingPage), eq(new ConfluencePage("1234", "Existing Page", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithExistingPageAndReplaceAncestorStrategy_sendsUpdate() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(existingPage);
        when(confluenceClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", REPLACE_ANCESTOR, confluenceClientMock, confluencePublisherListenerMock, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, never()).addPageUnderAncestor(any(), any(), any(), any(), any());
        verify(confluenceClientMock).updatePage(eq("72189173"), eq(null), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"), eq(2), eq(null), eq(true));
        verify(confluencePublisherListenerMock).pageUpdated(existingPage, new ConfluencePage("72189173", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2));
        verify(confluencePublisherListenerMock).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_whenAttachmentsHaveSameContentHash_doesNotUpdateAttachments() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceClientMock.getAttachmentByFileName("72189173", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "/download/attachmentOne.txt", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH)).thenReturn(sha256Hex("attachment1"));

        when(confluenceClientMock.getAttachmentByFileName("72189173", "attachmentTwo.txt")).thenReturn(new ConfluenceAttachment("att2", "attachmentTwo.txt", "/download/attachmentTwo.txt", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH)).thenReturn(sha256Hex("attachment2"));

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, confluenceClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, never()).addAttachment(any(), any(), any());
        verify(confluenceClientMock, never()).updateAttachmentContent(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    public void publish_whenExistingAttachmentsHaveMissingHashProperty_updatesAttachmentsAndHashProperties() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceClientMock.getAttachmentByFileName("72189173", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH)).thenReturn(null);

        when(confluenceClientMock.getAttachmentByFileName("72189173", "attachmentTwo.txt")).thenReturn(new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH)).thenReturn(null);

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, confluenceClientMock, confluencePublisherListenerMock, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, never()).deletePropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH);
        verify(confluenceClientMock).updateAttachmentContent(eq("72189173"), eq("att1"), eq("attachmentOne.txt"), any(FileInputStream.class), eq(true));
        verify(confluenceClientMock).setPropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH, sha256Hex("attachment1"));

        verify(confluenceClientMock, never()).deletePropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH);
        verify(confluenceClientMock).updateAttachmentContent(eq("72189173"), eq("att2"), eq("attachmentTwo.txt"), any(FileInputStream.class), eq(true));
        verify(confluenceClientMock).setPropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH, sha256Hex("attachment2"));

        verify(confluenceClientMock, never()).addAttachment(anyString(), anyString(), any(InputStream.class));

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1)), eq(new ConfluencePage("72189173", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).attachmentUpdated(eq("attachmentOne.txt"), eq("72189173"));
        verify(confluencePublisherListenerMock, times(1)).attachmentUpdated(eq("attachmentTwo.txt"), eq("72189173"));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_whenExistingAttachmentsHaveDifferentHashProperty_updatesAttachmentsAndHashProperties() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        ArgumentCaptor<InputStream> content = ArgumentCaptor.forClass(InputStream.class);

        when(confluenceClientMock.getAttachmentByFileName("72189173", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH)).thenReturn("otherHash1");

        when(confluenceClientMock.getAttachmentByFileName("72189173", "attachmentTwo.txt")).thenReturn(new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH)).thenReturn("otherHash2");

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, confluenceClientMock);

        // act
        confluencePublisher.publish();

        // assert
        InOrder inOrder = inOrder(confluenceClientMock);
        inOrder.verify(confluenceClientMock).deletePropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH);
        inOrder.verify(confluenceClientMock).updateAttachmentContent(eq("72189173"), eq("att1"), eq("attachmentOne.txt"), content.capture(), eq(true));
        inOrder.verify(confluenceClientMock).setPropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH, sha256Hex("attachment1"));
        assertThat(inputStreamAsString(content.getValue(), UTF_8), is("attachment1"));

        verify(confluenceClientMock).deletePropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH);
        verify(confluenceClientMock).updateAttachmentContent(eq("72189173"), eq("att2"), eq("attachmentTwo.txt"), content.capture(), eq(true));
        verify(confluenceClientMock).setPropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH, sha256Hex("attachment2"));
        assertThat(inputStreamAsString(content.getValue(), UTF_8), is("attachment2"));

        verify(confluenceClientMock, never()).addAttachment(anyString(), anyString(), any(InputStream.class));
    }

    @Test
    public void publish_whenNewAttachmentsAreEmpty_deletesAttachmentsPresentOnConfluence() {
        //arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceClientMock.getAttachments("72189173")).thenReturn(asList(
                new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1),
                new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1)
        ));

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", REPLACE_ANCESTOR, confluenceClientMock, confluencePublisherListenerMock, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock).deleteAttachment("att1");
        verify(confluenceClientMock).deletePropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH);

        verify(confluenceClientMock).deleteAttachment("att2");
        verify(confluenceClientMock).deletePropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH);

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1)), eq(new ConfluencePage("72189173", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).attachmentDeleted(eq("attachmentOne.txt"), eq("72189173"));
        verify(confluencePublisherListenerMock, times(1)).attachmentDeleted(eq("attachmentTwo.txt"), eq("72189173"));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_whenSomePreviouslyAttachedFilesHaveBeenRemovedFromPage_deletesAttachmentsNotPresentUnderPage() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceClientMock.getAttachments("72189173")).thenReturn(asList(
                new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1),
                new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1),
                new ConfluenceAttachment("att3", "attachmentThree.txt", "", 1)
        ));

        when(confluenceClientMock.getAttachmentByFileName("72189173", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH)).thenReturn(sha256Hex("attachment1"));

        when(confluenceClientMock.getAttachmentByFileName("72189173", "attachmentTwo.txt")).thenReturn(new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1));
        when(confluenceClientMock.getPropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH)).thenReturn(sha256Hex("attachment2"));

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, confluenceClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, never()).deleteAttachment("att1");
        verify(confluenceClientMock, never()).deletePropertyByKey("72189173", ATTACHMENT_ONE_SHA256_HASH);

        verify(confluenceClientMock, never()).deleteAttachment("att2");
        verify(confluenceClientMock, never()).deletePropertyByKey("72189173", ATTACHMENT_TWO_SHA256_HASH);

        verify(confluenceClientMock).deleteAttachment("att3");
        verify(confluenceClientMock).deletePropertyByKey("72189173", ATTACHMENT_THREE_SHA256_HASH);
    }

    @Test
    public void publish_metadataWithOneExistingPageButConfluencePageHasMissingHashPropertyValue_pageIsUpdatedAndHashPropertyIsSet() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("12", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 1);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getChildPages("1234")).thenReturn(singletonList(existingPage));
        when(confluenceClientMock.getPageByTitle("~personalSpace", "1234", "Some Confluence Content")).thenReturn("12");
        when(confluenceClientMock.getPageWithContentAndVersionById("12")).thenReturn(existingPage);
        when(confluenceClientMock.getPropertyByKey("12", CONTENT_HASH_PROPERTY_KEY)).thenReturn(null);

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key", confluenceClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(1)).setPropertyByKey("12", CONTENT_HASH_PROPERTY_KEY, SOME_CONFLUENCE_CONTENT_SHA256_HASH);
    }

    @Test
    public void publish_metadataWithMultipleRemovedPagesInHierarchyForAppendToAncestorPublishingStrategy_sendsDeletePageRequestForEachRemovedPage() {
        // arrange
        ConfluencePage existingParentPage = new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2);
        ConfluencePage existingChildPage = new ConfluencePage("3456", "Some Child Content", "<h1>Some Child Content</h1>", 3);
        ConfluencePage existingChildChildPage = new ConfluencePage("4567", "Some Child Child Content", "<h1>Some Child Child Content</h1>", 3);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getChildPages("1234")).thenReturn(singletonList(existingParentPage));
        when(confluenceClientMock.getChildPages("2345")).thenReturn(singletonList(existingChildPage));
        when(confluenceClientMock.getChildPages("3456")).thenReturn(singletonList(existingChildChildPage));

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("zero-page", confluenceClientMock, confluencePublisherListenerMock, "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(1)).deletePage(eq("2345"));
        verify(confluenceClientMock, times(1)).deletePage(eq("3456"));
        verify(confluenceClientMock, times(1)).deletePage(eq("4567"));

        verify(confluencePublisherListenerMock, times(1)).pageDeleted(eq(new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).pageDeleted(eq(new ConfluencePage("3456", "Some Child Content", "<h1>Some Child Content</h1>", 3)));
        verify(confluencePublisherListenerMock, times(1)).pageDeleted(eq(new ConfluencePage("4567", "Some Child Child Content", "<h1>Some Child Child Content</h1>", 3)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithMultipleRemovedPagesInHierarchyForAppendToAncestorPublishingStrategyAndKeepOrphansEnabled_doesNotDeleteRemovedPages() {
        // arrange
        ConfluencePage existingParentPage = new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2);
        ConfluencePage existingChildPage = new ConfluencePage("3456", "Some Child Content", "<h1>Some Child Content</h1>", 3);
        ConfluencePage existingChildChildPage = new ConfluencePage("4567", "Some Child Child Content", "<h1>Some Child Child Content</h1>", 3);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getChildPages("1234")).thenReturn(singletonList(existingParentPage));
        when(confluenceClientMock.getChildPages("2345")).thenReturn(singletonList(existingChildPage));
        when(confluenceClientMock.getChildPages("3456")).thenReturn(singletonList(existingChildChildPage));

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("zero-page", APPEND_TO_ANCESTOR, KEEP_ORPHANS, confluenceClientMock, confluencePublisherListenerMock, "version message", true);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(0)).deletePage(eq("2345"));
        verify(confluenceClientMock, times(0)).deletePage(eq("3456"));
        verify(confluenceClientMock, times(0)).deletePage(eq("4567"));
    }

    @Test
    public void publish_metadataWithMultipleRemovedPagesInHierarchyForReplaceAncestorPublishingStrategy_sendsDeletePageRequestForEachRemovedPageExceptAncestor() {
        // arrange
        ConfluencePage ancestorPage = new ConfluencePage("1234", "Some Ancestor Content", "<h1>Some Ancestor Content</h1>", 1);
        ConfluencePage existingParentPage = new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2);
        ConfluencePage existingChildPage = new ConfluencePage("3456", "Some Child Content", "<h1>Some Child Content</h1>", 3);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById("1234")).thenReturn(ancestorPage);
        when(confluenceClientMock.getChildPages("1234")).thenReturn(singletonList(existingParentPage));
        when(confluenceClientMock.getChildPages("2345")).thenReturn(singletonList(existingChildPage));

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("ancestor-only", REPLACE_ANCESTOR, confluenceClientMock, confluencePublisherListenerMock, "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(1)).updatePage(eq("1234"), eq(null), eq("Ancestor Page"), eq("<h1>Some Ancestor Content</h1>"), eq(2), eq("version message"), eq(true));
        verify(confluenceClientMock, times(1)).deletePage(eq("2345"));
        verify(confluenceClientMock, times(1)).deletePage(eq("3456"));

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(ancestorPage), eq(new ConfluencePage("1234", "Ancestor Page", "<h1>Some Ancestor Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).pageDeleted(eq(new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).pageDeleted(eq(new ConfluencePage("3456", "Some Child Content", "<h1>Some Child Content</h1>", 3)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_labels_withoutLabelOnPage() {
        // arrange
        ConfluencePage confluencePage = new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 1);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle("~personalSpace", "1234", "Some Confluence Content")).thenReturn("2345");
        when(confluenceClientMock.getPageWithContentAndVersionById("2345")).thenReturn(confluencePage);
        when(confluenceClientMock.getPropertyByKey("2345", CONTENT_HASH_PROPERTY_KEY)).thenReturn("7a901829ba6a0b6f7f084ae4313bdb5d83bc2c4ea21b452ba7073c0b0c60faae");
        when(confluenceClientMock.getLabels("2345")).thenReturn(emptyList());

        ConfluencePublisher confluencePublisher = confluencePublisher("page-with-labels", confluenceClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(1)).getLabels(eq("2345"));
        verify(confluenceClientMock, times(0)).deleteLabel(eq("2345"), any(String.class));
        verify(confluenceClientMock, times(1)).addLabels(eq("2345"), eq(asList("label-one", "label-two")));
    }

    @Test
    public void publish_labels_withLabelsOnPage() {
        // arrange
        ConfluencePage confluencePage = new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 1);

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle("~personalSpace", "1234", "Some Confluence Content")).thenReturn("2345");
        when(confluenceClientMock.getPageWithContentAndVersionById("2345")).thenReturn(confluencePage);
        when(confluenceClientMock.getPropertyByKey("2345", CONTENT_HASH_PROPERTY_KEY)).thenReturn("7a901829ba6a0b6f7f084ae4313bdb5d83bc2c4ea21b452ba7073c0b0c60faae");
        when(confluenceClientMock.getLabels("2345")).thenReturn(asList("label-two", "obsolete-label"));

        ConfluencePublisher confluencePublisher = confluencePublisher("page-with-labels", confluenceClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(1)).getLabels(eq("2345"));
        verify(confluenceClientMock, times(1)).deleteLabel(eq("2345"), eq("obsolete-label"));
        verify(confluenceClientMock, times(0)).deleteLabel(eq("2345"), eq("label-two"));
        verify(confluenceClientMock, times(1)).addLabels(eq("2345"), eq(singletonList("label-one")));
    }

    @Test
    public void publish_existingPagePresentInChildPagesOfAncestor_doesNotLookUpPageByTitle() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getChildPages("1234")).thenReturn(singletonList(new ConfluencePage("3456", "Existing Page", 1)));
        when(confluenceClientMock.getPageWithContentAndVersionById("3456")).thenReturn(new ConfluencePage("3456", "Existing Page", "<h1>Some Other Confluence Content</h1>", 1));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id", confluenceClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, never()).getPageByTitle(any(), any(), any());
        verify(confluenceClientMock, never()).addPageUnderAncestor(any(), any(), any(), any(), any());
        verify(confluenceClientMock, times(1)).updatePage(eq("3456"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2), any(), eq(true));
    }

    @Test
    public void publish_existingPageWithSameCombinedHash_onlyReadsCombinedHashProperty() {
        // arrange
        String combinedHash = combinedHashSetWhenPublishing("root-ancestor-id-page-with-attachments");

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPropertyByKey("72189173", COMBINED_HASH_PROPERTY_KEY)).thenReturn(combinedHash);

        ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, confluenceClientMock, confluencePublisherListenerMock, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceClientMock, times(1)).getPropertyByKey("72189173", COMBINED_HASH_PROPERTY_KEY);
        verify(confluenceClientMock, times(1)).getChildPages("72189173");
        verifyNoMoreInteractions(confluenceClientMock);

        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_existingPageWithDifferentCombinedHash_invalidatesCombinedHashBeforePublishingAndSetsNewCombinedHashAfterwards() {
        // arrange
        String combinedHash = combinedHashSetWhenPublishing("root-ancestor-id-page-with-attachments");

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPropertyByKey("72189173", COMBINED_HASH_PROPERTY_KEY)).thenReturn("someOtherCombinedHash");
        when(confluenceClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceClientMock.getAttachmentByFileName(any(), any())).thenThrow(new NotFoundException());

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, confluenceClientMock);

        // act
        confluencePublisher.publish();

        // assert
        InOrder inOrder = inOrder(confluenceClientMock);
        inOrder.verify(confluenceClientMock).deletePropertyByKey("72189173", COMBINED_HASH_PROPERTY_KEY);
        inOrder.verify(confluenceClientMock).updatePage(eq("72189173"), any(), any(), any(), eq(2), any(), eq(true));
        inOrder.verify(confluenceClientMock, times(2)).addAttachment(eq("72189173"), any(), any());
        inOrder.verify(confluenceClientMock).setPropertyByKey("72189173", COMBINED_HASH_PROPERTY_KEY, combinedHash);
    }

    @Test
    public void publish_withOwnership_newPage_addsOwnerLabelAndSourceProperty() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("2345");

        when(confluenceClientMock.getLabels("2345")).thenReturn(singletonList("syseng-src-abc"));

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock).publish();

        // assert
        InOrder inOrder = inOrder(confluenceClientMock);
        inOrder.verify(confluenceClientMock).addPageUnderAncestor(any(), any(), any(), any(), any());
        inOrder.verify(confluenceClientMock).addLabels("2345", singletonList("syseng-src-abc"));
        inOrder.verify(confluenceClientMock).setPropertyByKey(eq("2345"), eq(CONTENT_HASH_PROPERTY_KEY), any());
        verify(confluenceClientMock).setPropertyByKey("2345", "syseng-source", "{\"repo\":\"group/repo\",\"path\":\"docs/meetings/notes/some-doc.adoc\"}");
    }

    @Test
    public void publish_withOwnership_emptyPathPrefix_usesSourcePathOnly() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("2345");

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock, new Ownership("syseng-src-abc", "group/repo", "")).publish();

        // assert
        verify(confluenceClientMock).setPropertyByKey("2345", "syseng-source", "{\"repo\":\"group/repo\",\"path\":\"notes/some-doc.adoc\"}");
    }

    @Test
    public void publish_withOwnership_metadataAlreadyHasOwnerLabel_ownerLabelSentOnce() {
        // arrange: after the label-first write, the page reports the owner label
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("2345");
        when(confluenceClientMock.getLabels("2345")).thenReturn(singletonList("syseng-src-abc"));

        // act
        ownedConfluencePublisher("one-page-with-source-and-owner-label", confluenceClientMock).publish();

        // assert
        InOrder inOrder = inOrder(confluenceClientMock);
        inOrder.verify(confluenceClientMock).addLabels("2345", singletonList("syseng-src-abc"));
        inOrder.verify(confluenceClientMock).addLabels("2345", singletonList("other-label"));
        verify(confluenceClientMock, times(2)).addLabels(any(), any());
        verify(confluenceClientMock, never()).deleteLabel(any(), any());
    }

    @Test
    public void publish_withOwnership_pathPrefixWithTrailingSlashAndDotSlash_isNormalized() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("2345");

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock, new Ownership("syseng-src-abc", "group/repo", "./docs/meetings/")).publish();

        // assert
        verify(confluenceClientMock).setPropertyByKey("2345", "syseng-source", "{\"repo\":\"group/repo\",\"path\":\"docs/meetings/notes/some-doc.adoc\"}");
    }

    @Test
    public void publish_existingPageWithoutOwnerLabel_throwsBeforeUpdate() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle("~personalSpace", "72189173", "Some Confluence Content")).thenReturn("72");
        when(confluenceClientMock.getPropertyByKey("72", COMBINED_HASH_PROPERTY_KEY)).thenReturn("someOtherCombinedHash");
        when(confluenceClientMock.getLabels("72")).thenReturn(singletonList("other"));

        // act
        RuntimeException e = assertThrows(RuntimeException.class, () -> ownedConfluencePublisher("one-page-with-source", confluenceClientMock).publish());

        // assert
        PageOwnedElsewhereException cause = (PageOwnedElsewhereException) e.getCause();
        assertThat(cause.getMessage(), is("page 'Some Confluence Content' (id 72) exists in space but was not published from group/repo; rename the doc or set a title prefix"));
        assertThat(cause.getTitle(), is("Some Confluence Content"));
        assertThat(cause.getContentId(), is("72"));
        verify(confluenceClientMock, never()).updatePage(any(), any(), any(), any(), anyInt(), any(), anyBoolean());
        verify(confluenceClientMock, never()).deletePropertyByKey(any(), any());
        verify(confluenceClientMock, never()).setPropertyByKey(any(), any(), any());
    }

    @Test
    public void publish_existingPageFromChildListingWithoutOwnerLabel_throwsBeforeUpdate() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getChildPages("72189173")).thenReturn(singletonList(new ConfluencePage("72", "Some Confluence Content", 1)));
        when(confluenceClientMock.getPropertyByKey("72", COMBINED_HASH_PROPERTY_KEY)).thenReturn("someOtherCombinedHash");
        when(confluenceClientMock.getLabels("72")).thenReturn(emptyList());

        // act + assert
        RuntimeException e = assertThrows(RuntimeException.class, () -> ownedConfluencePublisher("one-page-with-source", confluenceClientMock).publish());
        assertThat(e.getCause() instanceof PageOwnedElsewhereException, is(true));
        verify(confluenceClientMock, never()).updatePage(any(), any(), any(), any(), anyInt(), any(), anyBoolean());
        verify(confluenceClientMock, never()).deletePropertyByKey(any(), any());
    }

    @Test
    public void publish_existingOwnedPageUnchanged_makesNoLabelRequest() {
        // arrange
        String ownedHash = combinedHashSetWhenPublishingOwned("one-page-with-source");

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenReturn("72");
        when(confluenceClientMock.getPropertyByKey("72", COMBINED_HASH_PROPERTY_KEY)).thenReturn(ownedHash);

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock).publish();

        // assert
        verify(confluenceClientMock, never()).getLabels(any());
        verify(confluenceClientMock, never()).updatePage(any(), any(), any(), any(), anyInt(), any(), anyBoolean());
        verify(confluenceClientMock, never()).setPropertyByKey(any(), any(), any());
    }

    @Test
    public void publish_existingOwnedPageChanged_deletesThenSetsSourceProperty() {
        // arrange
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenReturn("72");
        when(confluenceClientMock.getPropertyByKey("72", COMBINED_HASH_PROPERTY_KEY)).thenReturn("someOtherCombinedHash");
        when(confluenceClientMock.getLabels("72")).thenReturn(singletonList("syseng-src-abc"));
        when(confluenceClientMock.getPageWithContentAndVersionById("72")).thenReturn(new ConfluencePage("72", "Some Confluence Content", "<h1>old</h1>", 1));

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock).publish();

        // assert
        InOrder inOrder = inOrder(confluenceClientMock);
        inOrder.verify(confluenceClientMock).updatePage(eq("72"), any(), any(), any(), eq(2), any(), anyBoolean());
        inOrder.verify(confluenceClientMock).deletePropertyByKey("72", "syseng-source");
        inOrder.verify(confluenceClientMock).setPropertyByKey("72", "syseng-source", "{\"repo\":\"group/repo\",\"path\":\"docs/meetings/notes/some-doc.adoc\"}");
    }

    @Test
    public void publish_withOwnership_foundPagePublishedFromExistingFileOutsidePrefix_throwsBeforeAnyWrite(@TempDir Path repoRoot) throws IOException {
        // arrange
        Files.createDirectories(repoRoot.resolve("docs/other"));
        Files.write(repoRoot.resolve("docs/other/x.adoc"), "= X".getBytes(UTF_8));
        ConfluenceClient confluenceClientMock = ownedPageFoundWithSource("{\"repo\":\"group/repo\",\"path\":\"docs/other/x.adoc\"}");

        // act
        RuntimeException e = assertThrows(RuntimeException.class, () -> ownedConfluencePublisher("one-page-with-source", confluenceClientMock, new Ownership("syseng-src-abc", "group/repo", "docs/meetings", repoRoot)).publish());

        // assert
        PageOwnedElsewhereException cause = (PageOwnedElsewhereException) e.getCause();
        assertThat(cause.getMessage(), is("page 'Some Confluence Content' (id 72) is published from docs/other/x.adoc in this repo; titles must be unique within a space"));
        assertNoWrites(confluenceClientMock);
    }

    @Test
    public void publish_withOwnership_foundPagePublishedFromOutsidePrefixWithoutRepoRoot_throwsBeforeAnyWrite() {
        // arrange
        ConfluenceClient confluenceClientMock = ownedPageFoundWithSource("{\"repo\":\"group/repo\",\"path\":\"docs/other/x.adoc\"}");

        // act
        RuntimeException e = assertThrows(RuntimeException.class, () -> ownedConfluencePublisher("one-page-with-source", confluenceClientMock).publish());

        // assert
        assertThat(e.getCause().getMessage(), is("page 'Some Confluence Content' (id 72) is published from docs/other/x.adoc in this repo; titles must be unique within a space"));
        assertNoWrites(confluenceClientMock);
    }

    @Test
    public void publish_withOwnership_foundPageMovedFromAnotherDestination_updatesAndReparents(@TempDir Path repoRoot) {
        // arrange: docs/other/x.adoc no longer exists, so the doc moved here
        ConfluenceClient confluenceClientMock = ownedPageFoundWithSource("{\"repo\":\"group/repo\",\"path\":\"docs/other/x.adoc\"}");
        when(confluenceClientMock.getPageWithContentAndVersionById("72")).thenReturn(new ConfluencePage("72", "Some Confluence Content", "<h1>old</h1>", 1, "555"));

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock, new Ownership("syseng-src-abc", "group/repo", "docs/meetings", repoRoot)).publish();

        // assert
        verify(confluenceClientMock).updatePage(eq("72"), eq("72189173"), eq("Some Confluence Content"), any(), eq(2), any(), anyBoolean());
        verify(confluenceClientMock).setPropertyByKey("72", "syseng-source", "{\"repo\":\"group/repo\",\"path\":\"docs/meetings/notes/some-doc.adoc\"}");
    }

    @Test
    public void publish_withOwnership_unchangedContentUnderOtherParent_isReparented() {
        // arrange
        ConfluenceClient confluenceClientMock = ownedPageFoundWithSource("{\"repo\":\"group/repo\",\"path\":\"docs/meetings/notes/some-doc.adoc\"}");
        when(confluenceClientMock.getPropertyByKey("72", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);
        when(confluenceClientMock.getPageWithContentAndVersionById("72")).thenReturn(new ConfluencePage("72", "Some Confluence Content", "<h1>old</h1>", 4, "555"));

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock).publish();

        // assert
        verify(confluenceClientMock).updatePage(eq("72"), eq("72189173"), eq("Some Confluence Content"), any(), eq(5), any(), anyBoolean());
    }

    @Test
    public void publish_withOwnership_unchangedContentUnderSameParent_isNotUpdated() {
        // arrange
        ConfluenceClient confluenceClientMock = ownedPageFoundWithSource("{\"repo\":\"group/repo\",\"path\":\"docs/meetings/notes/some-doc.adoc\"}");
        when(confluenceClientMock.getPropertyByKey("72", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);
        when(confluenceClientMock.getPageWithContentAndVersionById("72")).thenReturn(new ConfluencePage("72", "Some Confluence Content", "<h1>old</h1>", 4, "72189173"));

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock).publish();

        // assert
        verify(confluenceClientMock, never()).updatePage(any(), any(), any(), any(), anyInt(), any(), anyBoolean());
    }

    @Test
    public void publish_withOwnership_emptyPathPrefix_neverRefusesForPath() {
        // arrange
        ConfluenceClient confluenceClientMock = ownedPageFoundWithSource("{\"repo\":\"group/repo\",\"path\":\"anywhere/x.adoc\"}");
        when(confluenceClientMock.getPageWithContentAndVersionById("72")).thenReturn(new ConfluencePage("72", "Some Confluence Content", "<h1>old</h1>", 1, "72189173"));

        // act
        ownedConfluencePublisher("one-page-with-source", confluenceClientMock, new Ownership("syseng-src-abc", "group/repo", "")).publish();

        // assert
        verify(confluenceClientMock).updatePage(eq("72"), eq("72189173"), any(), any(), eq(2), any(), anyBoolean());
    }

    private static ConfluenceClient ownedPageFoundWithSource(String sourceProperty) {
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenReturn("72");
        when(confluenceClientMock.getPropertyByKey("72", COMBINED_HASH_PROPERTY_KEY)).thenReturn("someOtherCombinedHash");
        when(confluenceClientMock.getPropertyByKey("72", "syseng-source")).thenReturn(sourceProperty);
        when(confluenceClientMock.getLabels("72")).thenReturn(singletonList("syseng-src-abc"));

        return confluenceClientMock;
    }

    private static void assertNoWrites(ConfluenceClient confluenceClientMock) {
        verify(confluenceClientMock, never()).updatePage(any(), any(), any(), any(), anyInt(), any(), anyBoolean());
        verify(confluenceClientMock, never()).addPageUnderAncestor(any(), any(), any(), any(), any());
        verify(confluenceClientMock, never()).deletePropertyByKey(any(), any());
        verify(confluenceClientMock, never()).setPropertyByKey(any(), any(), any());
        verify(confluenceClientMock, never()).addLabels(any(), any());
        verify(confluenceClientMock, never()).deleteLabel(any(), any());
        verify(confluenceClientMock, never()).deletePage(any());
    }

    @Test
    public void publish_withoutOwnership_combinedHashUnchanged() {
        // arrange: the hash vail.3 computes for a page without labels or attachments, spelled out independently of the production code
        String expectedHash = sha256Hex("title:Some Confluence Content\ncontent:" + SOME_CONFLUENCE_CONTENT_SHA256_HASH + "\n");

        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenReturn("72");
        when(confluenceClientMock.getPropertyByKey("72", COMBINED_HASH_PROPERTY_KEY)).thenReturn(expectedHash);

        // act
        confluencePublisher("one-page-with-source", confluenceClientMock).publish();

        // assert
        verify(confluenceClientMock, never()).getLabels(any());
        verify(confluenceClientMock, never()).updatePage(any(), any(), any(), any(), anyInt(), any(), anyBoolean());
        verify(confluenceClientMock, never()).setPropertyByKey(any(), any(), any());
    }

    private static String combinedHashSetWhenPublishingOwned(String qualifier) {
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageByTitle(anyString(), anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("2345");

        ownedConfluencePublisher(qualifier, confluenceClientMock).publish();

        ArgumentCaptor<String> combinedHash = ArgumentCaptor.forClass(String.class);
        verify(confluenceClientMock).setPropertyByKey(any(), eq(COMBINED_HASH_PROPERTY_KEY), combinedHash.capture());

        return combinedHash.getValue();
    }

    private static ConfluencePublisher ownedConfluencePublisher(String qualifier, ConfluenceClient confluenceClient) {
        return ownedConfluencePublisher(qualifier, confluenceClient, new Ownership("syseng-src-abc", "group/repo", "docs/meetings"));
    }

    private static ConfluencePublisher ownedConfluencePublisher(String qualifier, ConfluenceClient confluenceClient, Ownership ownership) {
        Path metadataFilePath = Paths.get(TEST_RESOURCES + "/metadata-" + qualifier + ".json");
        ConfluencePublisherMetadata metadata = readConfig(metadataFilePath);
        resolveAbsoluteContentFileAndAttachmentsPath(metadata.getPages(), metadataFilePath.getParent().toAbsolutePath());

        return new ConfluencePublisher(metadata, APPEND_TO_ANCESTOR, REMOVE_ORPHANS, confluenceClient, mock(ConfluencePublisherListener.class), null, true, ownership);
    }

    private static String combinedHashSetWhenPublishing(String qualifier) {
        ConfluenceClient confluenceClientMock = mock(ConfluenceClient.class);
        when(confluenceClientMock.getPageWithContentAndVersionById(any())).thenReturn(new ConfluencePage("any", "any", "any", 1));
        when(confluenceClientMock.getAttachmentByFileName(any(), any())).thenThrow(new NotFoundException());

        confluencePublisher(qualifier, REPLACE_ANCESTOR, confluenceClientMock).publish();

        ArgumentCaptor<String> combinedHash = ArgumentCaptor.forClass(String.class);
        verify(confluenceClientMock).setPropertyByKey(any(), eq(COMBINED_HASH_PROPERTY_KEY), combinedHash.capture());

        return combinedHash.getValue();
    }

    private static ConfluencePublisher confluencePublisher(String qualifier, ConfluenceClient confluenceRestClient) {
        return confluencePublisher(qualifier, APPEND_TO_ANCESTOR, REMOVE_ORPHANS, confluenceRestClient, mock(ConfluencePublisherListener.class), null, true);
    }

    private static ConfluencePublisher confluencePublisher(String qualifier, PublishingStrategy publishingStrategy, String versionMessage) {
        return confluencePublisher(qualifier, publishingStrategy, REMOVE_ORPHANS, mock(ConfluenceClient.class), mock(ConfluencePublisherListener.class), versionMessage, true);
    }

    private static ConfluencePublisher confluencePublisher(String qualifier, PublishingStrategy publishingStrategy, ConfluenceClient confluenceRestClient) {
        return confluencePublisher(qualifier, publishingStrategy, REMOVE_ORPHANS, confluenceRestClient, mock(ConfluencePublisherListener.class), null, true);
    }

    private static ConfluencePublisher confluencePublisher(String qualifier, ConfluenceClient confluenceRestClient, ConfluencePublisherListener confluencePublisherListener, String versionedMessage) {
        return confluencePublisher(qualifier, APPEND_TO_ANCESTOR, REMOVE_ORPHANS, confluenceRestClient, confluencePublisherListener, versionedMessage, true);
    }

    private static ConfluencePublisher confluencePublisher(String qualifier, PublishingStrategy publishingStrategy, ConfluenceClient confluenceRestClient, ConfluencePublisherListener confluencePublisherListener, String versionMessage) {
        return confluencePublisher(qualifier, publishingStrategy, REMOVE_ORPHANS, confluenceRestClient, confluencePublisherListener, versionMessage, true);
    }

    private static ConfluencePublisher confluencePublisher(String qualifier, PublishingStrategy publishingStrategy, OrphanRemovalStrategy orphanRemovalStrategy, ConfluenceClient confluenceRestClient, ConfluencePublisherListener confluencePublisherListener, String versionMessage, boolean notifyWatchers) {
        Path metadataFilePath = Paths.get(TEST_RESOURCES + "/metadata-" + qualifier + ".json");
        Path contentRoot = metadataFilePath.getParent().toAbsolutePath();

        ConfluencePublisherMetadata metadata = readConfig(metadataFilePath);
        resolveAbsoluteContentFileAndAttachmentsPath(metadata.getPages(), contentRoot);

        return new ConfluencePublisher(metadata, publishingStrategy, orphanRemovalStrategy, confluenceRestClient, confluencePublisherListener, versionMessage, notifyWatchers);
    }

    private static ConfluencePublisherMetadata readConfig(Path metadataFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        try {
            return objectMapper.readValue(newInputStream(metadataFile), ConfluencePublisherMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read metadata", e);
        }
    }

    private static void resolveAbsoluteContentFileAndAttachmentsPath(List<ConfluencePageMetadata> pages, Path contentRoot) {
        pages.forEach((page) -> {
            page.setContentFilePath(contentRoot.resolve(page.getContentFilePath()).toString());
            page.setAttachments(page.getAttachments().entrySet().stream().collect(toMap(
                    (entry) -> entry.getValue(),
                    (entry) -> contentRoot.resolve(entry.getKey()).toString()
            )));

            resolveAbsoluteContentFileAndAttachmentsPath(page.getChildren(), contentRoot);
        });
    }

}
