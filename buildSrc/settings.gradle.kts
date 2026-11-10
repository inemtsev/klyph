// buildSrc compiles the convention plugins against the klyph Gradle plugin, which the engine
// modules apply through klyph.native. Including the plugin's build here substitutes its sources
// for the com.eventslooped.klyph:klyph-gradle-plugin dependency in build.gradle.kts; the root
// settings include the same build for the executables, and Gradle builds it once.
includeBuild("../klyph-gradle-plugin")

// The root build reads gradle/libs.versions.toml by itself; buildSrc is a build of its own and
// imports the same file.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
