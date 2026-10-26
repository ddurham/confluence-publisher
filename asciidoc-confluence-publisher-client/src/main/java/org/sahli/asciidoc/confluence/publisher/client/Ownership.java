package org.sahli.asciidoc.confluence.publisher.client;

/**
 * Identifies the repository a set of pages is published from. When present, the publisher labels every page,
 * records its repo-relative source path and refuses to overwrite pages that were not published from the repo.
 */
public final class Ownership {

    private final String label;
    private final String repo;
    private final String pathPrefix;

    public Ownership(String label, String repo, String pathPrefix) {
        this.label = label;
        this.repo = repo;
        this.pathPrefix = pathPrefix;
    }

    public String label() {
        return this.label;
    }

    public String repo() {
        return this.repo;
    }

    public String pathPrefix() {
        return this.pathPrefix;
    }

}
