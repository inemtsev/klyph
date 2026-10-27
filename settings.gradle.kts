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
}

rootProject.name = "klyph"

include(":klyph-platform")
include(":klyph")
include(":sandbox")
include(":samples")
