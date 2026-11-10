// Standalone build for the klyph Gradle plugin. The engine build includes it with
// pluginManagement { includeBuild("klyph-gradle-plugin") }, and it also builds on its own:
// ../gradlew -p klyph-gradle-plugin check
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    // The engine build's version catalog, which the root build reads by itself.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "klyph-gradle-plugin"
