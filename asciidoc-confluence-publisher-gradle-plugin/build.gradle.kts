plugins {
    `java-gradle-plugin`
    `maven-publish`
}

val nexusPublicUrl = "https://nexus.vail/repository/maven-public/"

repositories {
    mavenLocal()
    // the name keeps it alive under the Vail init script, which drops every other repository
    maven {
        name = "VailNexus"
        url = uri(nexusPublicUrl)
    }
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
}

dependencies {
    // only compiled against: the publisher (AsciidoctorJ, JRuby) runs in a forked worker, never in the build's JVM
    compileOnly("org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-cli:$version")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
    options.encoding = "UTF-8"
}

gradlePlugin {
    plugins {
        create("asciidocConfluencePublisher") {
            id = "org.sahli.asciidoc.confluence.publisher"
            implementationClass = "org.sahli.asciidoc.confluence.publisher.gradle.plugin.AsciidocConfluencePublisherPlugin"
            displayName = "Asciidoc Confluence Publisher"
            description = "Publishes an asciidoc folder to Confluence"
        }
    }
}

// the plugin puts the publisher of its own version on the worker classpath
val generatePluginVersion by tasks.registering {
    val versionFile = layout.buildDirectory.file("generated/resources/plugin-version/asciidoc-confluence-publisher-gradle-plugin.properties")
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    outputs.file(versionFile)
    doLast {
        versionFile.get().asFile.writeText("version=$pluginVersion\n")
    }
}

sourceSets.main {
    resources.srcDir(generatePluginVersion.map { layout.buildDirectory.dir("generated/resources/plugin-version") })
}

tasks.test {
    useJUnitPlatform()
    // the functional tests resolve the publisher from ~/.m2 (./mvnw install) and Nexus
    systemProperty("nexusPublicUrl", nexusPublicUrl)
}

// the Vail init script adds a `mavenJava` publication with the coordinates of `pluginMaven`
tasks.withType<AbstractPublishToMaven>().configureEach {
    onlyIf("not the init script's duplicate publication") { publication.name != "mavenJava" }
}

// the task's properties are documented where it helps; doclint would want every getter
tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}
