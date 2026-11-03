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
}

rootProject.name = "klyph-gradle-plugin"
