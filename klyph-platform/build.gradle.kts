import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("klyph.native")
}

val sdl3Prefix: String = Sdl3.prefix(project)

kotlin {
    explicitApi()

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops.create("sdl3") {
                definitionFile.set(file("src/nativeInterop/cinterop/sdl3.def"))
                packageName("sdl3")
                includeDirs("$sdl3Prefix/include")
            }
            // stb_image, vendored; its implementation is compiled into the interop stubs.
            cinterops.create("stb") {
                definitionFile.set(file("src/nativeInterop/cinterop/stb.def"))
                packageName("stb")
                includeDirs("src/nativeInterop/c/stb")
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
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
