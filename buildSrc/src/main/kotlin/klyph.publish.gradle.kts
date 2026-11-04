// Convention plugin for the published engine modules (klyph, klyph-platform): Maven coordinates,
// the POM, signing and the Central Portal repository, through com.vanniktech.maven.publish.
//
// Credentials are Gradle properties, from ~/.gradle/gradle.properties or ORG_GRADLE_PROJECT_*
// environment variables: mavenCentralUsername and mavenCentralPassword for the upload,
// signingInMemoryKey and signingInMemoryKeyPassword for the signatures. Without
// signingInMemoryKey the publications are not signed, so publishToMavenLocal works in CI; the
// Central Portal rejects unsigned uploads, so a release needs the key.

plugins {
    id("klyph.native")
    id("klyph.docs")
    id("com.vanniktech.maven.publish")
}

// A release carries every target. Publishing a subset would put a root module on the repository
// whose metadata names only some platforms, so any publish task fails before it starts unless
// all targets are configured. Only a macOS host builds every target (Kotlin/Native cannot run
// the macOS cinterop elsewhere, so other hosts skip macosArm64), so uploads to Maven Central
// must also come from macOS; publishToMavenLocal is allowed anywhere, which is what CI runs.
val selectedTargets: List<String> = KlyphTargets.selected(project)
val missingTargets: List<String> = KlyphTargets.all - selectedTargets.toSet()
val projectPath: String = path
val configuredTargets: String = selectedTargets.joinToString()
val hostTarget: String = KlyphTargets.host
gradle.taskGraph.whenReady {
    val publishTasks = allTasks.filter { it.project.path == projectPath && it.name.startsWith("publish") }
    if (publishTasks.isNotEmpty() && missingTargets.isNotEmpty()) {
        throw GradleException(
            "Publishing $projectPath needs every target; run with -Pklyph.targets=all " +
                "(configured: $configuredTargets; missing: ${missingTargets.joinToString()}).",
        )
    }
    if (hostTarget != "macosArm64" && publishTasks.any { it.name.contains("MavenCentral") }) {
        throw GradleException(
            "Publishing $projectPath to Maven Central needs a macOS host, the only one that builds every target (this host builds $hostTarget).",
        )
    }
}

val signingKeyPresent: Boolean = !providers.gradleProperty("signingInMemoryKey").orNull.isNullOrBlank()

mavenPublishing {
    publishToMavenCentral()
    if (signingKeyPresent) signAllPublications()

    coordinates(project.group.toString(), project.name, project.version.toString())

    pom {
        name.set(project.name)
        description.set(providers.provider { project.description })
        inceptionYear.set("2026")
        url.set("https://github.com/inemtsev/klyph")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("inemtsev")
                name.set("Ilya Nemtsev")
                url.set("https://github.com/inemtsev")
            }
        }
        scm {
            url.set("https://github.com/inemtsev/klyph")
            connection.set("scm:git:https://github.com/inemtsev/klyph.git")
            developerConnection.set("scm:git:ssh://git@github.com/inemtsev/klyph.git")
        }
    }
}
