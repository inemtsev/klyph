package com.eventslooped.klyph.gradle

import java.io.File

/**
 * Environment for run tasks. Each `KLYPH_*` variable can be set in the shell or as a Gradle
 * property, so IDE run configurations can pass `-Pklyph.sprites=50000`. A Gradle property wins
 * over the shell. File paths (`KLYPH_MUSIC`, and the path after the first colon of
 * `KLYPH_SCREENSHOT=<frame>:<path>`) resolve against the root project directory, since the tasks
 * run in their own module's directory.
 */
internal object RunEnvironment {
    val variables: List<String> = listOf(
        "SPRITES", "CHILDREN", "TEXTURES", "INTERPOLATE", "FRAMES", "SECONDS", "OFFSCREEN",
        "VSYNC", "VALIDATION", "AUTOPILOT", "MUSIC", "CELL", "OVERLAY", "SCREENSHOT", "FULLSCREEN",
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
            result[variable] = when (name) {
                in paths -> rootDirectory.resolve(value).normalize().path
                "SCREENSHOT" -> resolveScreenshot(value, rootDirectory)
                else -> value
            }
        }
        return result
    }

    /** Resolves the path of a `<frame>:<path>` screenshot value, leaving a value without a colon as it is. */
    private fun resolveScreenshot(value: String, rootDirectory: File): String {
        val colon = value.indexOf(':')
        if (colon < 0 || colon == value.length - 1) return value
        val path = rootDirectory.resolve(value.substring(colon + 1)).normalize().path
        return value.substring(0, colon + 1) + path
    }
}
