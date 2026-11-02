import org.gradle.api.Project

/**
 * Environment for the sandbox, shooter and benchmark run tasks. Each `KLYPH_*` variable can be set
 * in the shell or as a Gradle property, so IDE run configurations can pass `-Pklyph.sprites=50000`.
 * A Gradle property wins over the shell. File paths (`KLYPH_MUSIC`) resolve against the root
 * project directory, since the tasks run in their own module's directory.
 */
object RunEnvironment {
    private val variables = listOf("SPRITES", "CHILDREN", "TEXTURES", "INTERPOLATE", "FRAMES", "SECONDS", "OFFSCREEN", "VSYNC", "VALIDATION", "AUTOPILOT", "MUSIC", "CELL", "OVERLAY")

    private val paths = setOf("MUSIC")

    fun of(project: Project, defaults: Map<String, String> = emptyMap()): Map<String, String> {
        val result = LinkedHashMap(defaults)
        for (name in variables) {
            val variable = "KLYPH_$name"
            val property = "klyph." + name.lowercase()
            val value = project.providers.gradleProperty(property).orNull
                ?: project.providers.environmentVariable(variable).orNull
            if (value != null) result[variable] = if (name in paths) project.rootProject.file(value).path else value
        }
        return result
    }
}
