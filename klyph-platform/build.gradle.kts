import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("klyph.native")
    id("klyph.publish")
}

description = "The SDL3, stb_image, stb_image_write and stb_vorbis bindings and the thin Kotlin API over them that the klyph engine is built on."

kotlin {
    explicitApi()

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops.create("sdl3") {
                definitionFile.set(file("src/nativeInterop/cinterop/sdl3.def"))
                packageName("sdl3")
                // The include folder of the SDL3 the klyph plugin found (klyph.native applies it).
                includeDirs(klyph.sdl3IncludeDir)
            }
            // stb_image and stb_image_write, vendored; their implementations are compiled into the interop stubs.
            cinterops.create("stb") {
                definitionFile.set(file("src/nativeInterop/cinterop/stb.def"))
                packageName("stb")
                includeDirs("src/nativeInterop/c/stb")
            }
            // stb_vorbis, vendored and compiled into the interop stubs the same way.
            cinterops.create("vorbis") {
                definitionFile.set(file("src/nativeInterop/cinterop/vorbis.def"))
                packageName("vorbis")
                includeDirs("src/nativeInterop/c/stb")
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            // The module defines the platform plumbing and calls it everywhere.
            languageSettings.optIn("klyph.platform.KlyphPlatformApi")
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// This is the one module that is allowed to touch native interop.
tasks.named("checkNoInteropImports") {
    enabled = false
}
