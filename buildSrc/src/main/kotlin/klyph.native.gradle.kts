// Convention plugin for the engine modules (klyph-platform, klyph, samples). The klyph Gradle
// plugin's library mode, com.eventslooped.klyph.library, configures the Kotlin/Native targets
// selected by klyph.targets, finds SDL3 and adds the SDL3 link flags every test binary needs,
// exactly as it does for a game. This plugin adds what only the engine build wants on top: tests
// run on the host's own target, `check` compiles every other one, and, through klyph.lint, native
// interop stays inside klyph-platform. The executables apply com.eventslooped.klyph instead.
//
// The engine build expects an installed SDL3: the root gradle.properties sets
// klyph.sdl3.download=false, so the plugin stops with the folders it searched instead of
// downloading SDL's release.

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("com.eventslooped.klyph.library")
    id("klyph.lint")
}

// The plugin creates the targets once the build script has run, so everything below is lazy.
// A macOS host compiles every target and runs its cinterop with the toolchains Kotlin/Native
// keeps in ~/.konan; Linux and Windows hosts cannot build macosArm64, and Kotlin/Native disables
// it there with a warning.
val hostTarget: String = klyph.hostTarget.get()

kotlin {
    // Tests run only on the host's own target: another target's test binary cannot execute here.
    // The plugin already skips linking it; `check` still compiles the main and test sources of
    // every other target.
    targets.withType<KotlinNativeTarget>().matching { it.name != hostTarget }.configureEach {
        val target = this
        tasks.matching { it.name == target.name + "Test" }.configureEach { enabled = false }
        tasks.named("check") {
            dependsOn(target.compilations.getByName("main").compileTaskProvider)
            dependsOn(target.compilations.getByName("test").compileTaskProvider)
        }
    }
}

// With several targets, nativeMain is a shared source set compiled against the commonized
// interop; checking it here catches a declaration the commonizer leaves out.
val configuredTargets: Provider<List<String>> = klyph.configuredTargets
val metadataCompilations = tasks.matching { it.name.startsWith("compile") && it.name.endsWith("KotlinMetadata") }
tasks.named("check") {
    dependsOn(configuredTargets.map { if (it.size > 1) metadataCompilations.toList() else emptyList() })
}
