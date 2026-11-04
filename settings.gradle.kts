pluginManagement {
    // The klyph Gradle plugin, which the executables (sandbox, games/shooter, games/squish,
    // games/roller, benchmarks) apply as plugins { id("com.eventslooped.klyph") }, the same way a
    // game outside the repository does.
    includeBuild("klyph-gradle-plugin")
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
include(":games:shooter")
include(":games:squish")
include(":games:roller")
include(":benchmarks")
include(":samples")
