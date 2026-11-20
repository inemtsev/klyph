// Standalone build for klyph's own detekt rules, the API_STYLE.md rules stock detekt cannot
// express. The engine build includes it with includeBuild("tools/detekt-rules") and klyph.lint
// adds it to detektPlugins; it also builds on its own: ./gradlew -p tools/detekt-rules check
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // The engine build's version catalog, which the root build reads by itself.
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "detekt-rules"
