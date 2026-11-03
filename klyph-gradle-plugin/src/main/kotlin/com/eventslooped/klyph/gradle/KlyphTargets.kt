package com.eventslooped.klyph.gradle

import org.gradle.api.GradleException

/**
 * The Kotlin/Native targets klyph builds for, and how a build picks among them. The rules match
 * the engine build's own `buildSrc/src/main/kotlin/KlyphTargets.kt`, error texts included.
 */
internal object KlyphTargets {
    const val MACOS_ARM64 = "macosArm64"
    const val LINUX_X64 = "linuxX64"
    const val LINUX_ARM64 = "linuxArm64"
    const val MINGW_X64 = "mingwX64"

    /** Every target, in the order a build configures them. */
    val all: List<String> = listOf(MACOS_ARM64, LINUX_X64, LINUX_ARM64, MINGW_X64)

    /** Returns the target of the machine running the build, from `os.name` and `os.arch`. */
    fun host(osName: String, osArch: String): String = when {
        osName == "Mac OS X" && osArch == "aarch64" -> MACOS_ARM64
        osName == "Linux" && osArch == "aarch64" -> LINUX_ARM64
        osName == "Linux" -> LINUX_X64
        osName.startsWith("Windows") -> MINGW_X64
        else -> throw GradleException("Unsupported host: $osName $osArch.")
    }

    /** Returns the Kotlin/Native family name of [target]: `OSX`, `LINUX` or `MINGW`. */
    fun family(target: String): String = when (target) {
        MACOS_ARM64 -> "OSX"
        LINUX_X64, LINUX_ARM64 -> "LINUX"
        MINGW_X64 -> "MINGW"
        else -> throw GradleException("Unknown klyph target $target.")
    }

    /**
     * Returns the targets to configure, in the order of [all] with repeats dropped.
     *
     * The Gradle property `klyph.targets` (comma-separated) wins, then `klyph.target`, then the
     * extension's list. `host` names the host's own target. `all` selects every target, but only
     * as the whole value: inside a list it is an unknown name.
     *
     * @throws GradleException if both properties are set, a name is unknown, or nothing is named.
     */
    fun resolve(targetsProperty: String?, targetProperty: String?, extension: List<String>, host: String): List<String> {
        val list = targetsProperty?.trim()
        val single = targetProperty?.trim()
        if (list != null && single != null) {
            throw GradleException("Set klyph.targets or klyph.target, not both: klyph.targets=$list, klyph.target=$single.")
        }
        val value = list ?: single
        val entries = (value?.split(',') ?: extension).map { it.trim() }.filter { it.isNotEmpty() }
        val names = when {
            value == "all" -> all
            value == null && entries == listOf("all") -> all
            else -> entries.map { if (it == "host") host else it }
        }
        val unknown = names.filter { it !in all }
        if (unknown.isNotEmpty()) {
            throw GradleException(
                "Unknown klyph target ${unknown.joinToString()}. Use host, all, or a comma-separated list of ${all.joinToString()}.",
            )
        }
        if (names.isEmpty()) throw GradleException("No klyph target is selected. Use host, all, or a comma-separated list of ${all.joinToString()}.")
        return all.filter { it in names }
    }
}
