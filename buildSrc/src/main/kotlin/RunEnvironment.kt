import org.gradle.api.Project

/**
 * Environment for the sandbox and benchmark run tasks. Each `KLYPH_*` variable can be set in the
 * shell or as a Gradle property, so IDE run configurations can pass `-Pklyph.sprites=50000`.
 * A Gradle property wins over the shell.
 */
object RunEnvironment {
    private val variables = listOf("SPRITES", "CHILDREN", "TEXTURES", "FRAMES", "OFFSCREEN", "VSYNC", "VALIDATION")

    fun of(project: Project, defaults: Map<String, String> = emptyMap()): Map<String, String> {
        val result = LinkedHashMap(defaults)
        for (name in variables) {
            val variable = "KLYPH_$name"
            val property = "klyph." + name.lowercase()
            val value = project.providers.gradleProperty(property).orNull
                ?: project.providers.environmentVariable(variable).orNull
            if (value != null) result[variable] = value
        }
        return result
    }
}
