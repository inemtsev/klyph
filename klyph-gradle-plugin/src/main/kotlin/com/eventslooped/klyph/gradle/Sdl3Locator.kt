package com.eventslooped.klyph.gradle

import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File

/** Where a build takes SDL3 from: an installed prefix, or a prefix [FetchSdl3] fills. */
internal sealed interface Sdl3Location {
    /** Folder with `include/SDL3/SDL.h`, the library folder [Sdl3Locator.libraryDir] finds and, on Windows, `bin/SDL3.dll`. */
    val prefix: File

    class Installed(override val prefix: File) : Sdl3Location {
        override fun toString(): String = "Installed($prefix)"
    }

    class Download(override val prefix: File, val release: Sdl3Release) : Sdl3Location {
        override fun toString(): String = "Download(${release.fileName} into $prefix)"
    }
}

/**
 * Finds SDL3 for the host. Order: the `sdl3.prefix` Gradle property, the `klyph { sdl3Prefix }`
 * setting, then the usual Homebrew, source-build and Linux package locations. The first folder
 * that contains `include/SDL3/SDL.h` wins. With none, macOS and Windows download SDL's release
 * into the Gradle user home; Linux stops with the package names.
 */
internal object Sdl3Locator {
    const val HEADER: String = "include/SDL3/SDL.h"

    /** Returns the system locations in the order they are tried, for a user home of [home]. */
    fun systemCandidates(home: String): List<String> = listOf(
        "/opt/homebrew/opt/sdl3", // Homebrew on Apple Silicon, the formula's own folder
        "/opt/homebrew",
        "$home/.homebrew/opt/sdl3", // Homebrew installed in the home folder
        "$home/.homebrew",
        "/usr/local/opt/sdl3", // Homebrew on Intel Macs
        "/usr/local", // SDL built from source
        "/usr", // Linux packages
    )

    /**
     * Returns every folder to try, in order. [ignoreSystem] (the `klyph.sdl3.ignoreSystem`
     * property, meant for testing the download) leaves out the system locations.
     */
    fun candidates(propertyPrefix: String?, extensionPrefix: String?, ignoreSystem: Boolean, home: String): List<String> =
        listOfNotNull(propertyPrefix, extensionPrefix).filter { it.isNotBlank() } +
            if (ignoreSystem) emptyList() else systemCandidates(home)

    /**
     * Returns the folder of [prefix] that holds the SDL3 library for [target], which is what the
     * link's `-L` and rpath point at and where a distribution takes the library from.
     *
     * Linux tries `lib`, `lib64` (Fedora), `lib/x86_64-linux-gnu` and `lib/aarch64-linux-gnu`
     * (Debian and Ubuntu), in that order, for `libSDL3.so.0` or `libSDL3.so`. macOS tries `lib`
     * for `libSDL3.0.dylib`, `libSDL3.dylib` or `SDL3.framework`, and Windows tries `lib` for
     * `libSDL3.dll.a`. Returns `<prefix>/lib` when no folder has the library, such as a prefix
     * [FetchSdl3] has not filled yet, and leaves the error to the linker.
     */
    fun libraryDir(prefix: File, target: String, exists: (File) -> Boolean = File::exists): File {
        val (folders, names) = when (KlyphTargets.family(target)) {
            "LINUX" -> LINUX_LIBRARY_FOLDERS to listOf("libSDL3.so.0", "libSDL3.so")
            "OSX" -> listOf("lib") to listOf("libSDL3.0.dylib", "libSDL3.dylib", "SDL3.framework")
            else -> listOf("lib") to listOf("libSDL3.dll.a")
        }
        return folders.map { File(prefix, it) }.firstOrNull { folder -> names.any { exists(File(folder, it)) } }
            ?: File(prefix, "lib")
    }

    /** Folders [libraryDir] tries on Linux, relative to the prefix, in order. */
    val LINUX_LIBRARY_FOLDERS: List<String> = listOf("lib", "lib64", "lib/x86_64-linux-gnu", "lib/aarch64-linux-gnu")

    /** Returns the first of [candidates] whose `include/SDL3/SDL.h` exists, or `null`. */
    fun find(candidates: List<String>, exists: (File) -> Boolean = File::isFile): String? =
        candidates.firstOrNull { exists(File(it, HEADER)) }

    /**
     * Returns the location to build against: [found] if a candidate had the header, otherwise a
     * download of [version] for [host] into `<cacheRoot>/<version>/<host>`.
     *
     * @throws GradleException if nothing was found and SDL publishes no release for [host].
     */
    fun locate(found: String?, looked: List<String>, host: String, version: String, cacheRoot: File): Sdl3Location {
        if (found != null) return Sdl3Location.Installed(File(found))
        val release = Sdl3Release.forTarget(host, version) ?: throw GradleException(missingMessage(host, looked))
        return Sdl3Location.Download(File(cacheRoot, "$version/$host"), release)
    }

    /** Returns the message for a host with no SDL3 install and no release to download. */
    fun missingMessage(host: String, looked: List<String>): String {
        val searched = "Cannot find SDL3: no $HEADER under ${looked.joinToString()}."
        val override = "Pass another install folder with -Psdl3.prefix=<path> or klyph { sdl3Prefix = \"<path>\" }."
        return if (host == KlyphTargets.LINUX_X64 || host == KlyphTargets.LINUX_ARM64) {
            "$searched\n" +
                "Install the SDL3 development package: libsdl3-dev on Debian 13 and Ubuntu 25.04 or newer, " +
                "sdl3 on Arch, SDL3-devel on Fedora.\n" +
                "Older distributions have no package: build SDL from source as described in " +
                "https://github.com/libsdl-org/SDL/blob/main/docs/README-linux.md and install it, " +
                "for example into /usr/local.\n" +
                override
        } else {
            "$searched\n$override"
        }
    }
}

/**
 * Runs the lookup in [Sdl3Locator.find] as a configuration input, so the configuration cache is
 * dropped when an install appears or disappears.
 */
internal abstract class Sdl3PrefixSource : ValueSource<String, Sdl3PrefixSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val candidates: ListProperty<String>
    }

    override fun obtain(): String? = Sdl3Locator.find(parameters.candidates.get())
}

/**
 * Runs [Sdl3Locator.libraryDir] as a configuration input, so the configuration cache is dropped
 * when the library moves between the folders it tries.
 */
internal abstract class Sdl3LibraryDirSource : ValueSource<String, Sdl3LibraryDirSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val prefix: Property<String>
        val target: Property<String>
    }

    override fun obtain(): String = Sdl3Locator.libraryDir(File(parameters.prefix.get()), parameters.target.get()).path
}
