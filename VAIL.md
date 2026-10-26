# Vail fork of confluence-publisher

Fork of https://github.com/confluence-publisher/confluence-publisher (Apache-2.0), branched from
upstream tag `0.35.0`. It exists because Atlassian throttles the publisher's page-tree walk with
`429 Too Many Requests`, and upstream neither retries nor keeps the walk small.

## What differs from upstream

1. A request answered with 429 is sent again: `Retry-After` is honoured (exponential backoff from
   15 s otherwise, 15 min at most per wait), up to 6 times. Override the count with
   `-Dasciidoc-confluence-publisher.maxRetriesOnTooManyRequests=N` (0 disables).
2. v2 client: attachments are listed 250 per request instead of 2, the space id is looked up once
   per run, and page properties are deleted by id (upstream deletes by key, which matches nothing).
3. Each page carries one `combined-hash` property covering its title, content, labels and
   attachments. When it matches, nothing else is requested for that page. Consequence: labels or
   attachments changed by hand in Confluence are only put back when the page's sources change.
4. Ids of existing pages come from the parent's child listing instead of a title lookup per page.
5. Command line client (`asciidoc-confluence-publisher-cli`), released as a self-contained
   `-jar-with-dependencies` jar that also bundles `com.vailsys:confluence-softbreaks` (a source line
   break is not turned into a hard break next to a code span):
   - The token is read from the environment variable `CONFLUENCE_PUBLISHER_PASSWORD` (not a
     command line argument).
   - `metadataOutputFile=<path>` writes the published page tree as JSON, with each page's source
     path. The parent directory must exist.
   - `ownerLabel`, `ownerRepo` and `ownerPathPrefix` (all three or none) mark pages as owned by a
     repo and refuse to overwrite pages of another one. An empty `ownerPathPrefix=` means the
     repo root.
   - A failed publish prints `Publishing to Confluence failed: <root cause>` on stderr.

An unchanged page costs 2 requests (upstream: about 15 with four diagrams). The first publish of a
tree with this fork costs slightly more than upstream, because it writes the combined hash on
every page.

Items 1 and 2 are meant to go upstream; 3 changes the stored page properties and stays here.

## Counting requests

`TooManyRequestsRetry` logs one line per request at `java.util.logging` level `FINE`. Pass a
logging configuration through `MAVEN_OPTS=-Djava.util.logging.config.file=...` that sets
`org.sahli.asciidoc.confluence.publisher.client.http.TooManyRequestsRetry.level=FINE`.

## Building and releasing

The poms stay at `0.0.0-SNAPSHOT`, as upstream; the version is set at build time. JDK 17 works.
The Maven plugin, its two libraries and the CLI jar are released (not the docs or Docker image).

```bash
./mvnw versions:set -DnewVersion=0.35.0-vail.N -DgenerateBackupPoms=false   # do not commit
./mvnw deploy -pl asciidoc-confluence-publisher-maven-plugin,asciidoc-confluence-publisher-cli -am \
  -DaltDeploymentRepository=nexus::https://nexus.vail/repository/maven-releases/
git checkout -- . && git tag 0.35.0-vail.N && git push origin 0.35.0-vail.N
```

`maven-releases` is immutable: bump `N` to redeploy. Use `install` instead of `deploy` to try a
build from `~/.m2` first. The integration tests (`-Pintegration-tests`) need a real Confluence
Cloud site and are not run here.
