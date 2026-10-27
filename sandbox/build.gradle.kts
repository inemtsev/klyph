import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("klyph.native")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            executable {
                entryPoint = "klyph.sandbox.main"
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(project(":klyph"))
            implementation(project(":klyph-platform"))
        }
    }
}
