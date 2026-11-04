// Convention plugin for the modules in the API reference (klyph, klyph-platform): Dokka over their
// sources, with @sample bodies taken from the samples module and source links to GitHub. The root
// project applies org.jetbrains.dokka too and aggregates these modules into one site, which
// `./gradlew dokkaGenerate` writes to build/dokka/html. Dokka is not part of `check`.

plugins {
    id("org.jetbrains.dokka")
}

val sampleSources: File = rootDir.resolve("samples/src/nativeTest/kotlin")
val repositoryPath: String = projectDir.relativeTo(rootDir).invariantSeparatorsPath

dokka {
    dokkaSourceSets.configureEach {
        // Module documentation; Dokka takes the "# Module <name>" section matching this module
        // from each file. The site's landing page, docs/index.md, is the root project's include.
        includes.from(rootDir.resolve("docs/klyph.md"), rootDir.resolve("docs/klyph-platform.md"))
        sourceLink {
            localDirectory.set(projectDir.resolve("src"))
            remoteUrl("https://github.com/inemtsev/klyph/tree/main/$repositoryPath/src")
            remoteLineSuffix.set("#L")
        }
    }
    // The sources live in nativeMain, so that is where Dokka resolves @sample references. Each
    // sample root may belong to one source set only.
    dokkaSourceSets.matching { it.name == "nativeMain" }.configureEach {
        samples.from(sampleSources)
    }
}
