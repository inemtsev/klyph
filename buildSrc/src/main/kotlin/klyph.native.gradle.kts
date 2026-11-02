// Convention plugin shared by every klyph module: the Kotlin/Native target for this host and the
// rule that native interop stays inside klyph-platform.

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// One target per build: the host's own, or the one named by -Pklyph.target=<name>.
// Cross-compiling needs SDL3 headers and libraries for the other platform, so it is opt-in.
val hostOs: String = System.getProperty("os.name")
val hostArch: String = System.getProperty("os.arch")
val hostTarget: String = when {
    hostOs == "Mac OS X" && hostArch == "aarch64" -> "macosArm64"
    hostOs == "Linux" && hostArch == "aarch64" -> "linuxArm64"
    hostOs == "Linux" -> "linuxX64"
    hostOs.startsWith("Windows") -> "mingwX64"
    else -> throw GradleException("Unsupported host: $hostOs $hostArch.")
}
val targetName: String = providers.gradleProperty("klyph.target").getOrElse(hostTarget)

kotlin {
    when (targetName) {
        "macosArm64" -> macosArm64()
        "linuxX64" -> linuxX64()
        "linuxArm64" -> linuxArm64()
        "mingwX64" -> mingwX64()
        else -> throw GradleException("Unknown klyph.target '$targetName'.")
    }

    // Every binary (executables and test runners) links against SDL3, whose library name is
    // carried by the cinterop klib; only the search path and runtime path are added here.
    val sdl3Lib = Sdl3.prefix(project) + "/lib"
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

val checkNoInteropImports = tasks.register("checkNoInteropImports") {
    description = "Fails if a source file outside klyph-platform imports native interop packages."
    group = "verification"
    val sources = fileTree("src") { include("**/*.kt") }
    inputs.files(sources)
    // Generated sources (compileShaders) land in src, so scan after they are written.
    dependsOn(tasks.matching { it.name == "compileShaders" })
    // Plain values only: the configuration cache cannot serialize references to the script itself.
    val root = projectDir
    val interopImport = Regex("""^\s*import\s+(kotlinx\.cinterop|sdl3|cnames|platform|stb|vorbis)\.""")
    doLast {
        val offenders = sources.files.filter { file ->
            file.useLines { lines -> lines.any { interopImport.containsMatchIn(it) } }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Native interop imports are only allowed in klyph-platform:\n" +
                    offenders.joinToString("\n") { "  " + it.relativeTo(root) },
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkNoInteropImports)
}
