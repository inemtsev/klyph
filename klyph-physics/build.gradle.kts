plugins {
    id("klyph.native")
    id("klyph.publish")
}

description = "2D physics for klyph: particles, soft bodies, ropes, cloth and rigid bodies, solved with XPBD."

kotlin {
    explicitApi()

    sourceSets {
        nativeMain.dependencies {
            api(project(":klyph"))
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
