package com.eventslooped.klyph.gradle

/**
 * One of SDL's official release assets: the macOS disk image with `SDL3.xcframework`, or the
 * MinGW development package for Windows. Linux has no official binary.
 */
internal class Sdl3Release(val version: String, val target: String, val fileName: String) {
    val url: String
        get() = "https://github.com/libsdl-org/SDL/releases/download/release-$version/$fileName"

    /** Returns the pinned SHA-256 of [fileName], or `null` when the plugin pins none for it. */
    val sha256: String?
        get() = PINNED_SHA256[fileName]

    override fun toString(): String = "Sdl3Release($fileName for $target)"

    companion object {
        /** The SDL3 version klyph is built and tested against, and the default download. */
        const val DEFAULT_VERSION: String = "3.4.18"

        /**
         * SHA-256 of each release asset the plugin may download, keyed by file name. A download
         * that does not match is deleted and fails the build.
         */
        val PINNED_SHA256: Map<String, String> = mapOf(
            "SDL3-3.4.18.dmg" to "c1adf3da91108dbc3e4205da5dacebc203085d283fa89e86eb2c889abd03a853",
            "SDL3-devel-3.4.18-mingw.zip" to "cd98158c8d025a816f430a600b4e9526524e6219ee884ec72bd498b80fd3bf58",
        )

        /** Returns the release asset for [target], or `null` when SDL publishes none (Linux). */
        fun forTarget(target: String, version: String): Sdl3Release? = when (target) {
            KlyphTargets.MACOS_ARM64 -> Sdl3Release(version, target, "SDL3-$version.dmg")
            KlyphTargets.MINGW_X64 -> Sdl3Release(version, target, "SDL3-devel-$version-mingw.zip")
            else -> null
        }
    }
}
