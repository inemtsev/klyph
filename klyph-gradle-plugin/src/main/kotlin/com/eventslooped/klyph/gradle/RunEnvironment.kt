package com.eventslooped.klyph.gradle

import java.io.File

/**
 * Environment for run tasks. Each `KLYPH_*` variable can be set in the shell or as a Gradle
 * property, so IDE run configurations can pass `-Pklyph.sprites=50000`. A Gradle property wins
 * over the shell. File paths (`KLYPH_MUSIC`) resolve against the root project directory, since
 * the tasks run in their own module's directory.
 */
internal object RunEnvironment {
    val variables: List<String> = listOf(
        "SPRITES", "CHILDREN", "TEXTURES", "INTERPOLATE", "FRAMES", "SECONDS", "OFFSCREEN",
        "VSYNC", "VALIDATION", "AUTOPILOT", "MUSIC", "CELL", "OVERLAY",
    )

    private val paths = setOf("MUSIC")

    fun of(
        property: (String) -> String?,
        environment: (String) -> String?,
        rootDirectory: File,
        defaults: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val result = LinkedHashMap(defaults)
        for (name in variables) {
            val variable = "KLYPH_$name"
            val value = property("klyph." + name.lowercase()) ?: environment(variable) ?: continue
            result[variable] = if (name in paths) rootDirectory.resolve(value).normalize().path else value
        }
        return result
    }
}
