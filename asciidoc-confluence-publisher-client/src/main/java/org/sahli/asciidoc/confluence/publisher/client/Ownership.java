package org.sahli.asciidoc.confluence.publisher.client;

import java.nio.file.Path;

/**
 * Identifies the repository a set of pages is published from. When present, the publisher labels every page,
 * records its repo-relative source path and refuses to overwrite pages that were not published from the repo.
 * The optional repo root lets the publisher tell a doc that moved to another destination of the same repo from
 * a doc that still exists there under the same title.
 */
public final class Ownership {

    private final String label;
    private final String repo;
    private final String pathPrefix;
    private final Path repoRoot;

    public Ownership(String label, String repo, String pathPrefix) {
        this(label, repo, pathPrefix, null);
    }

    public Ownership(String label, String repo, String pathPrefix, Path repoRoot) {
        this.label = label;
        this.repo = repo;
        this.pathPrefix = pathPrefix;
        this.repoRoot = repoRoot;
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

    /**
     * The repo's working tree, or {@code null} when not known.
     */
    public Path repoRoot() {
        return this.repoRoot;
    }

}
