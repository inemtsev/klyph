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
