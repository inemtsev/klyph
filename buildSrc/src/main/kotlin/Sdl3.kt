import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Locates the SDL3 installation (its `include/` folder and the folder holding its library) once
 * per build.
 *
 * Order: the `sdl3.prefix` Gradle property, then the usual Homebrew, source-build and Linux
 * package locations. The first candidate that contains `include/SDL3/SDL.h` wins. The library
 * folder is probed the way the klyph Gradle plugin's `Sdl3Locator.libraryDir` does it, for the
 * host: on Linux `lib`, `lib64` (Fedora), `lib/x86_64-linux-gnu` and `lib/aarch64-linux-gnu`
 * (Debian and Ubuntu), elsewhere `lib`.
 */
object Sdl3 {
    private var cached: String? = null
    private var cachedLibDir: String? = null

    fun prefix(project: Project): String = cached ?: detect(project).also { cached = it }

    /** Returns the folder of [prefix] that holds the host's SDL3 library, or `<prefix>/lib` when none does. */
    fun libDir(project: Project): String = cachedLibDir ?: libraryDir(prefix(project), KlyphTargets.host).also { cachedLibDir = it }

    private fun libraryDir(prefix: String, target: String): String {
        val (folders, names) = when (target) {
            "linuxX64", "linuxArm64" -> listOf("lib", "lib64", "lib/x86_64-linux-gnu", "lib/aarch64-linux-gnu") to
                listOf("libSDL3.so.0", "libSDL3.so")
            "macosArm64" -> listOf("lib") to listOf("libSDL3.0.dylib", "libSDL3.dylib", "SDL3.framework")
            else -> listOf("lib") to listOf("libSDL3.dll.a")
        }
        return (folders.map { File(prefix, it) }.firstOrNull { folder -> names.any { File(folder, it).exists() } } ?: File(prefix, "lib")).path
    }

    private fun detect(project: Project): String {
        val home = System.getProperty("user.home")
        val candidates = listOfNotNull(
            project.providers.gradleProperty("sdl3.prefix").orNull,
            "/opt/homebrew/opt/sdl3", // Homebrew on Apple Silicon, the formula's own folder
            "/opt/homebrew",
            "$home/.homebrew/opt/sdl3", // Homebrew installed in the home folder
            "$home/.homebrew",
            "/usr/local/opt/sdl3", // Homebrew on Intel Macs
            "/usr/local", // SDL built from source
            "/usr", // Linux packages
        )
        val found = candidates.firstOrNull { File(it, "include/SDL3/SDL.h").exists() }
            ?: throw GradleException(
                "Cannot find SDL3 headers. Looked for include/SDL3/SDL.h under: ${candidates.joinToString()}.\n" +
                    "Install SDL3 (brew install sdl3), then pass its prefix with -Psdl3.prefix=<path> " +
                    "or put sdl3.prefix=<path> in gradle.properties.",
            )
        project.logger.lifecycle("Using SDL3 from $found")
        return found
    }
}
