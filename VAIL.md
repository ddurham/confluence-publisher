# Vail fork of confluence-publisher

Fork of https://github.com/confluence-publisher/confluence-publisher (Apache-2.0), rebased onto
upstream tag `0.36.0` (`0.35.0-vail.1` to `0.35.0-vail.7` were released from the branch on `0.35.0`,
`vail/publish-adoc`). It exists because Atlassian throttles the publisher's page-tree walk with
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
   - `CONFLUENCE_PUBLISHER_AUTHORIZATION`, when set, is sent verbatim as the `Authorization` header
     of every request, and `username` and the password are not needed (REST API v2 only). This is
     for a credential-injecting proxy (a Docker sandbox) that replaces a placeholder only where the
     header value is that placeholder, which Basic auth's base64 would hide.
   - `metadataOutputFile=<path>` writes the published page tree as JSON, with each page's source
     path. The parent directory must exist.
   - `ownerLabel`, `ownerRepo` and `ownerPathPrefix` (all three or none) mark pages as owned by a
     repo and refuse to overwrite pages of another one. An empty `ownerPathPrefix=` means the
     repo root. A changed page whose parent is not `ancestorId` is moved under it.
   - `ownerRepoRoot=<absolute path>` (optional, with the three above): a page found by title whose
     `syseng-source` path lies outside `ownerPathPrefix` is taken over only when that file no longer
     exists under the repo root (the doc moved here); otherwise, or without `ownerRepoRoot`, the
     publish is refused before any write, as two docs of the repo share a title in the space.
   - A failed publish prints `Publishing to Confluence failed: <root cause>` on stderr.
6. Gradle plugin `org.sahli.asciidoc.confluence.publisher` (`asciidoc-confluence-publisher-gradle-plugin`, a
   Gradle build of its own). Its `publishToConfluence` task takes the command line client's settings under the
   same names and runs the same code (`PublishRunner`) in a forked worker JVM, so AsciidoctorJ and JRuby never
   load into the build's JVM. The worker's classpath is the `confluencePublisher` configuration, which defaults
   to the CLI artifact of the plugin's version.
   - Defaults: `asciidocRootFolder` is `src/docs/asciidoc`, converted pages go to
     `build/asciidoc-confluence-publisher`, `authorization` comes from `CONFLUENCE_PUBLISHER_AUTHORIZATION`.
   - Credentials are per user and never stored by the build: `username` comes from
     `CONFLUENCE_PUBLISHER_USERNAME`, else the Gradle property `confluencePublisher.username` (in the user's
     own `~/.gradle/gradle.properties`). The token comes from `CONFLUENCE_PUBLISHER_PASSWORD`, else the first
     line printed by `passwordCommand` (`CONFLUENCE_PUBLISHER_PASSWORD_COMMAND`, else
     `confluencePublisher.passwordCommand`), a shell command such as `pass show confluence/token` run only
     when publishing.
   - `maxRetriesOnTooManyRequests` and `workerMaxHeapSize` (for example `1g`) configure the worker JVM.
   - Works with the configuration cache; the task is never up to date.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven { url = uri("https://nexus.vail/repository/maven-public/") }
    }
}

// build.gradle.kts
plugins {
    id("org.sahli.asciidoc.confluence.publisher") version "0.36.0-vail.N"
}

repositories {
    maven { url = uri("https://nexus.vail/repository/maven-public/") }
}

tasks.publishToConfluence {
    rootConfluenceUrl = "https://example.atlassian.net/wiki"
    spaceKey = "SPACE"
    ancestorId = "123456"
}
```

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
The Maven plugin, its two libraries, the CLI jar and the Gradle plugin are released (not the docs or Docker
image).

```bash
./mvnw versions:set -DnewVersion=0.36.0-vail.N -DgenerateBackupPoms=false   # do not commit
NEXUS_PASSWORD="$(pass show vail-nexus/password | head -n 1)" MAVEN_SKIP_RC=1 \
  ./mvnw deploy -pl asciidoc-confluence-publisher-maven-plugin,asciidoc-confluence-publisher-cli -am \
  -DaltDeploymentRepository=nexus::https://nexus.vail/repository/maven-releases/
(cd asciidoc-confluence-publisher-gradle-plugin && ./gradlew publish -Pversion=0.36.0-vail.N)
git checkout -- . && git tag 0.36.0-vail.N && git push origin 0.36.0-vail.N
```

No password is stored in a file; `pass` (or any command that prints it) supplies each one when a deploy needs it:

- Maven: the `nexus` server in `~/.m2/settings.xml` has `<password>${env.NEXUS_PASSWORD}</password>`, filled for
  the deploy command alone as above. `MAVEN_SKIP_RC=1` keeps `~/.mavenrc` out: one that exports
  `CONFLUENCE_PUBLISHER_PASSWORD` for poms naming `asciidoc-confluence-publisher-maven-plugin` matches this repo's
  root pom too, and the CLI tests that expect a missing password then fail. Build and test here without
  `CONFLUENCE_PUBLISHER_*` in the environment.
- Gradle: the plugin's Nexus publish repository comes from the Vail `~/.gradle/init.gradle`, whose duplicate
  `mavenJava` publication the build skips. Its credentials are `mavenUsername` and
  `mavenPasswordCommand=pass show vail-nexus/password` in `~/.gradle/gradle.properties`; the init script runs the
  command only when the build publishes to Nexus.

The Gradle plugin compiles against the CLI of its own version, so deploy the CLI first. To try a change locally,
run `MAVEN_SKIP_RC=1 ./mvnw install -pl asciidoc-confluence-publisher-cli -am` and then `./gradlew build` (or
`publishToMavenLocal`) in the plugin's directory; its functional tests take the publisher from `~/.m2`.

`maven-releases` is immutable: bump `N` to redeploy; it starts again at 1 on a new upstream base.
Use `install` instead of `deploy` to try a build from `~/.m2` first. The integration tests
(`-Pintegration-tests`) need a real Confluence Cloud site and are not run here.
