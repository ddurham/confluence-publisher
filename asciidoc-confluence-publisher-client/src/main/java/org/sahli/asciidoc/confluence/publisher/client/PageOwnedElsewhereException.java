package org.sahli.asciidoc.confluence.publisher.client;

public class PageOwnedElsewhereException extends RuntimeException {

    private final String title;
    private final String contentId;
    private final String repo;

    public PageOwnedElsewhereException(String title, String contentId, String repo) {
        super("page '" + title + "' (id " + contentId + ") exists in space but was not published from " + repo + "; rename the doc or set a title prefix");
        this.title = title;
        this.contentId = contentId;
        this.repo = repo;
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
