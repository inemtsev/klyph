plugins {
    id("klyph.native")
}

kotlin {
    sourceSets {
        nativeTest.dependencies {
            implementation(project(":klyph"))
            implementation(project(":klyph-platform"))
            implementation(kotlin("test"))
        }
    }
}

// The guide's Kotlin snippets are copies of functions in src/nativeTest/kotlin/samples/guide/,
// which compile with the samples; this check fails when a page and its source drift apart.
val checkGuideSnippets = tasks.register<CheckGuideSnippetsTask>("checkGuideSnippets") {
    description = "Checks that the Kotlin snippets of the guide pages in docs/ match their sources."
    pages.from(rootProject.layout.projectDirectory.dir("docs").asFileTree.matching { include("*.md") })
    sources.from(layout.projectDirectory.dir("src/nativeTest/kotlin/samples/guide").asFileTree)
    rootDirectory.set(rootProject.layout.projectDirectory)
}

tasks.named("check") { dependsOn(checkGuideSnippets) }
