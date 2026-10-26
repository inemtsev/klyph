import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

plugins {
    id("klyph.native")
}

val sdl3Prefix: String = Sdl3.prefix(project)

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            executable {
                entryPoint = "klyph.sandbox.main"
                linkerOpts("-L$sdl3Prefix/lib")
                // Let the binary find libSDL3 at runtime wherever it was installed. Not a thing on Windows.
                if (konanTarget.family != Family.MINGW) linkerOpts("-Wl,-rpath,$sdl3Prefix/lib")
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
