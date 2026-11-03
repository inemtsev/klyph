// Convention plugin for the engine modules (klyph-platform, klyph, samples): the Kotlin/Native
// targets selected by klyph.targets, the SDL3 link flags their test binaries need, and, through
// klyph.lint, the rule that native interop stays inside klyph-platform. The executables build
// with the com.eventslooped.klyph plugin instead, which does the same for games.

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.konan.target.Family
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("klyph.lint")
}

// The targets named by -Pklyph.targets (host, all, or a comma-separated list; see KlyphTargets).
// A macOS host compiles every target and runs its cinterop with the toolchains Kotlin/Native keeps
// in ~/.konan; Linux and Windows hosts cannot build macosArm64, and Kotlin/Native disables it there
// with a warning. Linking needs an SDL3 library built for the target, so foreign targets are opt-in.
val selectedTargets: List<String> = KlyphTargets.selected(project)

kotlin {
    for (targetName in selectedTargets) {
        when (targetName) {
            "macosArm64" -> macosArm64()
            "linuxX64" -> linuxX64()
            "linuxArm64" -> linuxArm64()
            "mingwX64" -> mingwX64()
        }
    }

    // Tests run only on the host's own target: another target's test binary cannot execute here,
    // and linking it needs that target's SDL3 library. `check` still compiles the main and test
    // sources of every other target, without linking them.
    targets.withType<KotlinNativeTarget>().matching { it.name != KlyphTargets.host }.configureEach {
        val target = this
        binaries.withType<TestExecutable>().configureEach {
            linkTaskProvider.configure { enabled = false }
        }
        tasks.matching { it.name == target.name + "Test" }.configureEach { enabled = false }
        tasks.named("check") {
            dependsOn(target.compilations.getByName("main").compileTaskProvider)
            dependsOn(target.compilations.getByName("test").compileTaskProvider)
        }
    }

    // Every binary (executables and test runners) links against SDL3, whose library name is
    // carried by the cinterop klib; only the search path and runtime path are added here.
    // Engine binaries link for the host only (see above), so the library folder is the host's:
    // lib/, or lib64/ and lib/<architecture>-linux-gnu/ for Fedora and Debian packages.
    val sdl3Lib = Sdl3.libDir(project)
    val sdl3Bin = Sdl3.prefix(project) + "/bin"
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.configureEach {
            linkerOpts("-L$sdl3Lib")
            if (konanTarget.family != Family.MINGW) linkerOpts("-Wl,-rpath,$sdl3Lib")
            // Kotlin/Native links Linux binaries against its own sysroot with an old glibc, while
            // libSDL3.so from a current distro references newer glibc symbol versions. Skip the
            // link-time check; the loader resolves them against the host glibc at run time.
            if (konanTarget.family == Family.LINUX) linkerOpts("--allow-shlib-undefined")
            // Windows has no rpath; it loads DLLs from the executable's own folder, so every link
            // (executables and test runners) copies SDL3.dll next to its output. The link task
            // does the copy itself, so the DLL is part of its outputs and its build cache entry.
            if (konanTarget.family == Family.MINGW) {
                val sdl3Dll = File(sdl3Bin, "SDL3.dll")
                linkTaskProvider.configure {
                    inputs.files(sdl3Dll).withPropertyName("sdl3Dll").withPathSensitivity(PathSensitivity.NONE)
                    // Plain values only: the configuration cache cannot serialize the script.
                    val destination = destinationDirectory
                    doLast {
                        if (!sdl3Dll.isFile) {
                            throw GradleException("Cannot find $sdl3Dll to copy next to the binary. Check -Psdl3.prefix.")
                        }
                        sdl3Dll.copyTo(destination.get().asFile.resolve(sdl3Dll.name), overwrite = true)
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    // With several targets, nativeMain is a shared source set compiled against the commonized
    // interop; checking it here catches a declaration the commonizer leaves out.
    if (selectedTargets.size > 1) {
        dependsOn(tasks.matching { it.name.startsWith("compile") && it.name.endsWith("KotlinMetadata") })
    }
}
