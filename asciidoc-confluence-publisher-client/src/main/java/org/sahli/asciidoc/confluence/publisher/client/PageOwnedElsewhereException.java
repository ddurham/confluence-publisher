package org.sahli.asciidoc.confluence.publisher.client;

public class PageOwnedElsewhereException extends RuntimeException {

    private final String title;
    private final String contentId;
    private final String repo;

    public PageOwnedElsewhereException(String title, String contentId, String repo) {
        this(title, contentId, repo, "page '" + title + "' (id " + contentId + ") exists in space but was not published from " + repo + "; rename the doc or set a title prefix");
    }

    private PageOwnedElsewhereException(String title, String contentId, String repo, String message) {
        super(message);
        this.title = title;
        this.contentId = contentId;
        this.repo = repo;
    }

    /**
     * The page was published from another doc of the same repo, which still exists: two docs share a title.
     */
    public static PageOwnedElsewhereException publishedFromOtherDoc(String title, String contentId, String repo, String sourcePath) {
        return new PageOwnedElsewhereException(title, contentId, repo, "page '" + title + "' (id " + contentId + ") is published from " + sourcePath + " in this repo; titles must be unique within a space");
    }

    public String getTitle() {
        return this.title;
    }

    public String getContentId() {
        return this.contentId;
    }

    public String getRepo() {
        return this.repo;
    }

}
