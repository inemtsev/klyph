package com.eventslooped.klyph.gradle

import org.gradle.api.GradleException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Sdl3LocatorTest {
    private val home = "/home/player"
    private val cache = File("/cache/klyph/sdl3")

    @Test
    fun gradlePropertyComesBeforeExtensionAndSystem() {
        val candidates = Sdl3Locator.candidates("/from/property", "/from/extension", ignoreSystem = false, home = home)
        assertEquals(listOf("/from/property", "/from/extension") + Sdl3Locator.systemCandidates(home), candidates)
    }

    @Test
    fun explicitPrefixWinsOverSystemInstall() {
        val candidates = Sdl3Locator.candidates(null, "/my/sdl", ignoreSystem = false, home = home)
        val withHeader = setOf("/my/sdl", "/opt/homebrew/opt/sdl3").map { File(it, Sdl3Locator.HEADER) }.toSet()
        assertEquals("/my/sdl", Sdl3Locator.find(candidates) { it in withHeader })
    }

    @Test
    fun prefixWithoutHeaderFallsThroughToSystem() {
        val candidates = Sdl3Locator.candidates("/empty", null, ignoreSystem = false, home = home)
        val withHeader = setOf(File("/usr/local", Sdl3Locator.HEADER))
        assertEquals("/usr/local", Sdl3Locator.find(candidates) { it in withHeader })
    }

    @Test
    fun systemCandidatesKeepTheEngineOrder() {
        assertEquals(
            listOf(
                "/opt/homebrew/opt/sdl3",
                "/opt/homebrew",
                "$home/.homebrew/opt/sdl3",
                "$home/.homebrew",
                "/usr/local/opt/sdl3",
                "/usr/local",
                "/usr",
            ),
            Sdl3Locator.systemCandidates(home),
        )
    }

    @Test
    fun firstSystemCandidateWithHeaderWins() {
        val candidates = Sdl3Locator.candidates(null, null, ignoreSystem = false, home = home)
        val withHeader = setOf("/usr", "/opt/homebrew").map { File(it, Sdl3Locator.HEADER) }.toSet()
        assertEquals("/opt/homebrew", Sdl3Locator.find(candidates) { it in withHeader })
    }

    @Test
    fun ignoreSystemSkipsSystemCandidates() {
        val candidates = Sdl3Locator.candidates(null, "/my/sdl", ignoreSystem = true, home = home)
        assertEquals(listOf("/my/sdl"), candidates)
        assertNull(Sdl3Locator.find(candidates) { it.path.startsWith("/usr") })
    }

    @Test
    fun linuxLibraryInLib() {
        assertEquals("lib", libraryDir(KlyphTargets.LINUX_X64, "lib/libSDL3.so.0"))
        assertEquals("lib", libraryDir(KlyphTargets.LINUX_X64, "lib/libSDL3.so"))
    }

    @Test
    fun fedoraLibraryInLib64() {
        assertEquals("lib64", libraryDir(KlyphTargets.LINUX_X64, "lib64/libSDL3.so.0"))
    }

    @Test
    fun debianLibraryInMultiarchFolders() {
        assertEquals("lib/x86_64-linux-gnu", libraryDir(KlyphTargets.LINUX_X64, "lib/x86_64-linux-gnu/libSDL3.so.0"))
        assertEquals("lib/aarch64-linux-gnu", libraryDir(KlyphTargets.LINUX_ARM64, "lib/aarch64-linux-gnu/libSDL3.so"))
    }

    @Test
    fun linuxFoldersAreTriedInOrder() {
        val everywhere = Sdl3Locator.LINUX_LIBRARY_FOLDERS.map { "$it/libSDL3.so.0" }.toTypedArray()
        assertEquals("lib", libraryDir(KlyphTargets.LINUX_X64, *everywhere))
        assertEquals("lib64", libraryDir(KlyphTargets.LINUX_X64, *everywhere.drop(1).toTypedArray()))
        assertEquals("lib/x86_64-linux-gnu", libraryDir(KlyphTargets.LINUX_ARM64, *everywhere.drop(2).toTypedArray()))
        // Other files in lib/ do not count; only the SDL3 library does.
        assertEquals("lib64", libraryDir(KlyphTargets.LINUX_X64, "lib/libc.so.6", "lib64/libSDL3.so.0"))
    }

    @Test
    fun macOsLibraryInLib() {
        assertEquals("lib", libraryDir(KlyphTargets.MACOS_ARM64, "lib/libSDL3.0.dylib"))
        assertEquals("lib", libraryDir(KlyphTargets.MACOS_ARM64, "lib/SDL3.framework"))
        // macOS and Windows look in lib/ only.
        assertEquals("lib", libraryDir(KlyphTargets.MACOS_ARM64, "lib64/libSDL3.0.dylib"))
    }

    @Test
    fun windowsLibraryInLib() {
        assertEquals("lib", libraryDir(KlyphTargets.MINGW_X64, "lib/libSDL3.dll.a"))
        assertEquals("lib", libraryDir(KlyphTargets.MINGW_X64, "lib64/libSDL3.dll.a"))
    }

    @Test
    fun missingLibraryFallsBackToLib() {
        for (target in KlyphTargets.all) assertEquals("lib", libraryDir(target))
    }

    private fun libraryDir(target: String, vararg files: String): String {
        val prefix = File("/prefix")
        val present = files.map { File(prefix, it) }.toSet()
        return Sdl3Locator.libraryDir(prefix, target) { it in present }.relativeTo(prefix).invariantSeparatorsPath
    }

    @Test
    fun foundPrefixIsInstalled() {
        val location = Sdl3Locator.locate("/opt/homebrew/opt/sdl3", emptyList(), KlyphTargets.MACOS_ARM64, "3.4.18", cache)
        assertIs<Sdl3Location.Installed>(location)
        assertEquals(File("/opt/homebrew/opt/sdl3"), location.prefix)
    }

    @Test
    fun missingOnMacOsDownloadsIntoTheCache() {
        val location = Sdl3Locator.locate(null, emptyList(), KlyphTargets.MACOS_ARM64, "3.4.18", cache)
        assertIs<Sdl3Location.Download>(location)
        assertEquals(File(cache, "3.4.18/macosArm64"), location.prefix)
        assertEquals("SDL3-3.4.18.dmg", location.release.fileName)
        assertEquals("https://github.com/libsdl-org/SDL/releases/download/release-3.4.18/SDL3-3.4.18.dmg", location.release.url)
    }

    @Test
    fun missingOnWindowsDownloadsTheMingwPackage() {
        val location = Sdl3Locator.locate(null, emptyList(), KlyphTargets.MINGW_X64, "3.4.18", cache)
        assertIs<Sdl3Location.Download>(location)
        assertEquals(File(cache, "3.4.18/mingwX64"), location.prefix)
        assertEquals("SDL3-devel-3.4.18-mingw.zip", location.release.fileName)
    }

    @Test
    fun missingOnLinuxNamesThePackages() {
        val looked = Sdl3Locator.candidates(null, null, ignoreSystem = false, home = home)
        val failure = assertFailsWith<GradleException> {
            Sdl3Locator.locate(null, looked, KlyphTargets.LINUX_X64, "3.4.18", cache)
        }
        val message = failure.message.orEmpty()
        assertContains(message, "libsdl3-dev")
        assertContains(message, "Debian 13")
        assertContains(message, "Ubuntu 25.04")
        assertContains(message, "sdl3 on Arch")
        assertContains(message, "SDL3-devel on Fedora")
        assertContains(message, "README-linux.md")
        assertContains(message, "/usr/local")
        assertContains(message, "-Psdl3.prefix")
    }

    @Test
    fun hashTableHasBothAssetsOfTheDefaultVersion() {
        val version = Sdl3Release.DEFAULT_VERSION
        for (target in listOf(KlyphTargets.MACOS_ARM64, KlyphTargets.MINGW_X64)) {
            val release = assertNotNull(Sdl3Release.forTarget(target, version))
            val hash = assertNotNull(release.sha256)
            assertTrue(Regex("[0-9a-f]{64}").matches(hash), "SHA-256 of ${release.fileName} is not 64 hex digits: $hash")
        }
        assertEquals(setOf("SDL3-$version.dmg", "SDL3-devel-$version-mingw.zip"), Sdl3Release.PINNED_SHA256.keys)
    }

    @Test
    fun otherVersionsHaveNoPinnedHash() {
        assertNull(Sdl3Release.forTarget(KlyphTargets.MACOS_ARM64, "3.2.0")?.sha256)
        assertNull(Sdl3Release.forTarget(KlyphTargets.LINUX_X64, Sdl3Release.DEFAULT_VERSION))
    }
}
