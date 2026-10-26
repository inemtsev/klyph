import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Locates the SDL3 installation (its `include/` and `lib/` folders) once per build.
 *
 * Order: the `sdl3.prefix` Gradle property, then the usual Homebrew, source-build and Linux
 * package locations. The first candidate that contains `include/SDL3/SDL.h` wins.
 */
object Sdl3 {
    private var cached: String? = null

    fun prefix(project: Project): String = cached ?: detect(project).also { cached = it }

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
