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

import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceAttachment;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy.REMOVE_ORPHANS;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.REPLACE_ANCESTOR;
import static org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils.assertMandatoryParameter;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.fileContent;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluencePublisher {

    static final String CONTENT_HASH_PROPERTY_KEY = "content-hash";
    static final String ATTACHMENT_HASH_SUFFIX = "-attachment-hash";
    static final String COMBINED_HASH_PROPERTY_KEY = "combined-hash";
    static final int INITIAL_PAGE_VERSION = 1;

    private final ConfluencePublisherMetadata metadata;
    private final PublishingStrategy publishingStrategy;
    private final OrphanRemovalStrategy orphanRemovalStrategy;
    private final ConfluenceClient confluenceClient;
    private final ConfluencePublisherListener confluencePublisherListener;
    private final String versionMessage;
    private final boolean notifyWatchers;

    public ConfluencePublisher(ConfluencePublisherMetadata metadata, PublishingStrategy publishingStrategy, OrphanRemovalStrategy orphanRemovalStrategy,
                               ConfluenceClient confluenceClient, ConfluencePublisherListener confluencePublisherListener,
                               String versionMessage, boolean notifyWatchers) {
        this.metadata = metadata;
        this.publishingStrategy = publishingStrategy;
        this.orphanRemovalStrategy = orphanRemovalStrategy;
        this.confluenceClient = confluenceClient;
        this.confluencePublisherListener = confluencePublisherListener != null ? confluencePublisherListener : new NoOpConfluencePublisherListener();
        this.versionMessage = versionMessage;
        this.notifyWatchers = notifyWatchers;
    }

    public void publish() {
        assertMandatoryParameter(isNotBlank(this.metadata.getSpaceKey()), "spaceKey");
        assertMandatoryParameter(isNotBlank(this.metadata.getAncestorId()), "ancestorId");

        switch (this.publishingStrategy) {
            case APPEND_TO_ANCESTOR:
                startPublishingUnderAncestorId(this.metadata.getPages(), this.metadata.getSpaceKey(), this.metadata.getAncestorId());
                break;
            case REPLACE_ANCESTOR:
                startPublishingReplacingAncestorId(singleRootPage(this.metadata), this.metadata.getSpaceKey(), this.metadata.getAncestorId());
                break;
            default:
                throw new IllegalArgumentException("Invalid publishing strategy '" + this.publishingStrategy + "'");
        }

        this.confluencePublisherListener.publishCompleted();
    }

    private static ConfluencePageMetadata singleRootPage(ConfluencePublisherMetadata metadata) {
        List<ConfluencePageMetadata> rootPages = metadata.getPages();

        if (rootPages.size() > 1) {
            String rootPageTitles = rootPages.stream()
                    .map(page -> "'" + page.getTitle() + "'")
                    .collect(joining(", "));

            throw new IllegalArgumentException("Multiple root pages found (" + rootPageTitles + "), but '" + REPLACE_ANCESTOR + "' publishing strategy only supports one single root page");
        }

        if (rootPages.isEmpty()) {
            throw new IllegalArgumentException("No root page found, but '" + REPLACE_ANCESTOR + "' publishing strategy requires one single root page");
        }

        return rootPages.get(0);
    }

    private void startPublishingReplacingAncestorId(ConfluencePageMetadata rootPage, String spaceKey, String ancestorId) {
        if (rootPage != null) {
            updateExistingPageUnlessUnchanged(ancestorId, null, rootPage);

            startPublishingUnderAncestorId(rootPage.getChildren(), spaceKey, ancestorId);
        }
    }

    private void startPublishingUnderAncestorId(List<ConfluencePageMetadata> pages, String spaceKey, String ancestorId) {
        Map<String, String> existingChildPageIdsByTitle = emptyMap();

        if (this.orphanRemovalStrategy == REMOVE_ORPHANS || !pages.isEmpty()) {
            List<ConfluencePage> childPagesOnConfluence = this.confluenceClient.getChildPages(ancestorId);

            if (this.orphanRemovalStrategy == REMOVE_ORPHANS) {
                deleteConfluencePagesNotPresentUnderAncestor(pages, childPagesOnConfluence);
            }

            // child pages listing already contains the ids of existing pages, saves one lookup by title per page
            existingChildPageIdsByTitle = childPagesOnConfluence.stream()
                    .collect(toMap(ConfluencePage::getTitle, ConfluencePage::getContentId, (first, second) -> first));
        }

        for (ConfluencePageMetadata page : pages) {
            try {
                String contentId = addOrUpdatePageUnderAncestor(spaceKey, ancestorId, page, existingChildPageIdsByTitle.get(page.getTitle()));

                startPublishingUnderAncestorId(page.getChildren(), spaceKey, contentId);
            } catch (Exception e) {
                throw new RuntimeException("Could not publish page '" + page.getTitle() + "'", e);
            }
        }
    }

    private void deleteConfluencePagesNotPresentUnderAncestor(List<ConfluencePageMetadata> pagesToKeep, String ancestorId) {
        deleteConfluencePagesNotPresentUnderAncestor(pagesToKeep, this.confluenceClient.getChildPages(ancestorId));
    }

    private void deleteConfluencePagesNotPresentUnderAncestor(List<ConfluencePageMetadata> pagesToKeep, List<ConfluencePage> childPagesOnConfluence) {
        List<ConfluencePage> childPagesOnConfluenceToDelete = childPagesOnConfluence.stream()
                .filter(childPageOnConfluence -> pagesToKeep.stream().noneMatch(page -> page.getTitle().equals(childPageOnConfluence.getTitle())))
                .collect(toList());

        childPagesOnConfluenceToDelete.forEach(pageToDelete -> {
            List<ConfluencePage> pageScheduledForDeletionChildPagesOnConfluence = this.confluenceClient.getChildPages(pageToDelete.getContentId());
            pageScheduledForDeletionChildPagesOnConfluence.forEach(parentPageToDelete -> this.deleteConfluencePagesNotPresentUnderAncestor(emptyList(), pageToDelete.getContentId()));
            this.confluenceClient.deletePage(pageToDelete.getContentId());
            this.confluencePublisherListener.pageDeleted(pageToDelete);
        });
    }

    private void deleteConfluenceAttachmentsNotPresentUnderPage(String contentId, Map<String, String> attachments) {
        List<ConfluenceAttachment> confluenceAttachments = this.confluenceClient.getAttachments(contentId);

        confluenceAttachments.stream()
                .filter(confluenceAttachment -> attachments.keySet().stream().noneMatch(attachmentFileName -> attachmentFileName.equals(confluenceAttachment.getTitle())))
                .forEach(confluenceAttachment -> {
                    this.confluenceClient.deletePropertyByKey(contentId, getAttachmentHashKey(confluenceAttachment.getTitle()));
                    this.confluenceClient.deleteAttachment(confluenceAttachment.getId());
                    this.confluencePublisherListener.attachmentDeleted(confluenceAttachment.getTitle(), contentId);
                });
    }

    private String addOrUpdatePageUnderAncestor(String spaceKey, String ancestorId, ConfluencePageMetadata page, String knownContentId) {
        String contentId;

        try {
            contentId = knownContentId != null ? knownContentId : this.confluenceClient.getPageByTitle(spaceKey, ancestorId, page.getTitle());
        } catch (NotFoundException e) {
            String content = fileContent(page.getContentFilePath(), UTF_8);
            contentId = this.confluenceClient.addPageUnderAncestor(spaceKey, ancestorId, page.getTitle(), content, this.versionMessage);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, hash(content));
            this.confluencePublisherListener.pageAdded(new ConfluencePage(contentId, page.getTitle(), content, INITIAL_PAGE_VERSION));

            publishLabelsAndAttachments(contentId, page);
            this.confluenceClient.setPropertyByKey(contentId, COMBINED_HASH_PROPERTY_KEY, combinedHash(page));

            return contentId;
        }

        updateExistingPageUnlessUnchanged(contentId, ancestorId, page);

        return contentId;
    }

    private void updateExistingPageUnlessUnchanged(String contentId, String ancestorId, ConfluencePageMetadata page) {
        String newCombinedHash = combinedHash(page);
        String existingCombinedHash = this.confluenceClient.getPropertyByKey(contentId, COMBINED_HASH_PROPERTY_KEY);

        // one single property covers title, content, labels and attachments: nothing else to check for an unchanged page
        if (newCombinedHash.equals(existingCombinedHash)) {
            return;
        }

        if (existingCombinedHash != null) {
            // avoid a stale combined hash wrongly matching later in case publishing fails half-way
            this.confluenceClient.deletePropertyByKey(contentId, COMBINED_HASH_PROPERTY_KEY);
        }

        updatePage(contentId, ancestorId, page);
        publishLabelsAndAttachments(contentId, page);

        this.confluenceClient.setPropertyByKey(contentId, COMBINED_HASH_PROPERTY_KEY, newCombinedHash);
    }

    private void publishLabelsAndAttachments(String contentId, ConfluencePageMetadata page) {
        addOrUpdateLabels(contentId, page.getLabels());

        deleteConfluenceAttachmentsNotPresentUnderPage(contentId, page.getAttachments());
        addAttachments(contentId, page.getAttachments());
    }

    private static String combinedHash(ConfluencePageMetadata page) {
        StringBuilder combined = new StringBuilder();
        combined.append("title:").append(page.getTitle()).append('\n');
        combined.append("content:").append(hash(fileContent(page.getContentFilePath(), UTF_8))).append('\n');

        page.getLabels().stream()
                .sorted()
                .forEach((label) -> combined.append("label:").append(label).append('\n'));

        new TreeMap<>(page.getAttachments())
                .forEach((attachmentFileName, attachmentPath) -> combined.append("attachment:").append(attachmentFileName).append(':').append(hash(fileInputStream(Paths.get(attachmentPath)))).append('\n'));

        return hash(combined.toString());
    }

    private void updatePage(String contentId, String ancestorId, ConfluencePageMetadata page) {
        String content = fileContent(page.getContentFilePath(), UTF_8);
        ConfluencePage existingPage = this.confluenceClient.getPageWithContentAndVersionById(contentId);
        String existingContentHash = this.confluenceClient.getPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
        String newContentHash = hash(content);

        if (notSameHash(existingContentHash, newContentHash) || !existingPage.getTitle().equals(page.getTitle())) {
            this.confluenceClient.deletePropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
            int newPageVersion = existingPage.getVersion() + 1;
            this.confluenceClient.updatePage(contentId, ancestorId, page.getTitle(), content, newPageVersion, this.versionMessage, this.notifyWatchers);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, newContentHash);
            this.confluencePublisherListener.pageUpdated(existingPage, new ConfluencePage(contentId, page.getTitle(), content, newPageVersion));
        }
    }

    private void addAttachments(String contentId, Map<String, String> attachments) {
        attachments.forEach((attachmentFileName, attachmentPath) -> addOrUpdateAttachment(contentId, attachmentPath, attachmentFileName));
    }

    private void addOrUpdateAttachment(String contentId, String attachmentPath, String attachmentFileName) {
        Path absoluteAttachmentPath = absoluteAttachmentPath(attachmentPath);
        String newAttachmentHash = hash(fileInputStream(absoluteAttachmentPath));

        try {
            ConfluenceAttachment existingAttachment = this.confluenceClient.getAttachmentByFileName(contentId, attachmentFileName);
            String attachmentId = existingAttachment.getId();
            String existingAttachmentHash = this.confluenceClient.getPropertyByKey(contentId, getAttachmentHashKey(attachmentFileName));

            if (notSameHash(existingAttachmentHash, newAttachmentHash)) {
                if (existingAttachmentHash != null) {
                    this.confluenceClient.deletePropertyByKey(contentId, getAttachmentHashKey(attachmentFileName));
                }
                this.confluenceClient.updateAttachmentContent(contentId, attachmentId, attachmentFileName, fileInputStream(absoluteAttachmentPath), this.notifyWatchers);
                this.confluenceClient.setPropertyByKey(contentId, getAttachmentHashKey(attachmentFileName), newAttachmentHash);
                this.confluencePublisherListener.attachmentUpdated(attachmentFileName, contentId);
            }

        } catch (NotFoundException e) {
            this.confluenceClient.deletePropertyByKey(contentId, getAttachmentHashKey(attachmentFileName));
            this.confluenceClient.addAttachment(contentId, attachmentFileName, fileInputStream(absoluteAttachmentPath));
            this.confluenceClient.setPropertyByKey(contentId, getAttachmentHashKey(attachmentFileName), newAttachmentHash);
            this.confluencePublisherListener.attachmentAdded(attachmentFileName, contentId);
        }
    }

    private static String getAttachmentHashKey(String attachmentFileName) {
        return hash(attachmentFileName) + ATTACHMENT_HASH_SUFFIX;
    }

    private Path absoluteAttachmentPath(String attachmentPath) {
        return Paths.get(attachmentPath);
    }

    private void addOrUpdateLabels(String contentId, List<String> labels) {
        List<String> existingLabels = this.confluenceClient.getLabels(contentId);

        existingLabels.stream()
                .filter((existingLabel) -> !(labels.contains(existingLabel)))
                .forEach((labelToDelete) -> this.confluenceClient.deleteLabel(contentId, labelToDelete));

        List<String> labelsToAdd = labels.stream()
                .filter((label) -> !(existingLabels.contains(label)))
                .collect(toList());

        if (labelsToAdd.size() > 0) {
            this.confluenceClient.addLabels(contentId, labelsToAdd);
        }
    }

    private static boolean notSameHash(String actualHash, String newHash) {
        return actualHash == null || !actualHash.equals(newHash);
    }

    private static String hash(String content) {
        return sha256Hex(content);
    }

    private static String hash(InputStream content) {
        try {
            return sha256Hex(content);
        } catch (IOException e) {
            throw new RuntimeException("Could not compute hash from input stream", e);
        } finally {
            try {
                content.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static FileInputStream fileInputStream(Path filePath) {
        try {
            return new FileInputStream(filePath.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not find attachment ", e);
        }
    }


    private static class NoOpConfluencePublisherListener implements ConfluencePublisherListener {

        @Override
        public void pageAdded(ConfluencePage addedPage) {
        }

        @Override
        public void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage) {
        }

        @Override
        public void pageDeleted(ConfluencePage deletedPage) {
        }

        @Override
        public void attachmentAdded(String attachmentFileName, String contentId) {
        }

        @Override
        public void attachmentUpdated(String attachmentFileName, String contentId) {
        }

        @Override
        public void attachmentDeleted(String attachmentFileName, String contentId) {
        }

        @Override
        public void publishCompleted() {
        }

    }

}
