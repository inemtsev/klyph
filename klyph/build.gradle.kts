plugins {
    id("klyph.native")
}

kotlin {
    explicitApi()

    sourceSets {
        nativeMain.dependencies {
            api(project(":klyph-platform"))
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
