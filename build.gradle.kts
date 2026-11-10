// The API reference: one Dokka site over the published modules, written to build/dokka/html by
// `./gradlew dokkaGenerate`, with docs/index.md as its landing page and the guide pages of docs/
// rendered into build/guide/ and copied into its guide/ folder. Each module's own Dokka settings live in
// buildSrc/src/main/kotlin/klyph.docs.gradle.kts.
plugins {
    id("org.jetbrains.dokka")
}

// Maven coordinates of the published modules: com.eventslooped.klyph:<module>:<version>.
// -Pklyph.version=<v> overrides the version; the release workflow passes the tag's version here.
// The Gradle plugin, an included build, reads the same property in its own build file.
val klyphVersion: String = providers.gradleProperty("klyph.version").getOrElse("1.0.0")

// The group goes on the subprojects only: the root project is also named klyph, and with the same
// group its coordinates would equal :klyph's, so dokka(project(":klyph")) would resolve to the root.
version = klyphVersion
subprojects {
    group = "com.eventslooped.klyph"
    version = klyphVersion
}

dokka {
    moduleName.set("klyph")
    dokkaPublications.html {
        includes.from("docs/index.md")
    }
}

dependencies {
    dokka(project(":klyph"))
    dokka(project(":klyph-platform"))
}

// The guide, in reading order. Each page becomes guide/<name>.html in the site, after Dokka has
// written it, with the header and navigation of a Dokka page (RenderGuideTask in buildSrc).
// renderGuide writes to build/guide/, outside Dokka's output, and installGuide copies that into
// the site, so no two tasks declare the same output folder. Dokka
// turns a Markdown link to a page it did not generate into plain text, so docs/index.md links to the
// guide with <a href> tags; renderGuide fails on a Markdown link there and on any broken link.
val guidePages: List<String> = listOf(
    "getting-started",
    "sprites-and-animation",
    "entities-systems-and-scenes",
    "input",
    "audio",
    "collisions-and-particles",
    "platformers",
    "text-canvas-and-overlay",
    "shipping",
    "performance",
)

val dokkaHtml: Provider<Directory> = layout.buildDirectory.dir("dokka/html")

val renderGuide = tasks.register<RenderGuideTask>("renderGuide") {
    description = "Renders the guide pages in docs/ into build/guide/, styled as pages of the Dokka site."
    dependsOn("dokkaGeneratePublicationHtml")
    pages.from(guidePages.map { "docs/$it.md" })
    template.set(dokkaHtml.map { it.file("klyph/index.html") })
    landing.set(layout.projectDirectory.file("docs/index.md"))
    siteDirectory.set(dokkaHtml)
    outputDirectory.set(layout.buildDirectory.dir("guide"))
}

val installGuide = tasks.register<Sync>("installGuide") {
    description = "Copies the rendered guide into the Dokka site's guide/ folder."
    // After Dokka, which rewrites its whole output folder when it runs.
    dependsOn("dokkaGeneratePublicationHtml")
    from(renderGuide)
    into(dokkaHtml.map { it.dir("guide") })
}

tasks.named("dokkaGenerate") { dependsOn(installGuide) }
