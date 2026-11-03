package com.eventslooped.klyph.gradle

import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DistributionLayoutTest {
    private val prefix: File = createTempDirectory("klyph-prefix").toFile()

    @AfterTest
    fun cleanUp() {
        deleteTree(prefix.toPath())
    }

    @Test
    fun folderNameCarriesProjectVersionAndTarget() {
        assertEquals("shooter-0.1.0-macosArm64", DistributionLayout.folderName("shooter", "0.1.0", "macosArm64"))
        assertEquals("shooter-linuxX64", DistributionLayout.folderName("shooter", "unspecified", "linuxX64"))
    }

    @Test
    fun macOsShipsHomebrewDylib() {
        touch("lib/libSDL3.0.dylib")
        touch("lib/libSDL3.a")
        assertEquals(listOf("libSDL3.0.dylib"), shipped(KlyphTargets.MACOS_ARM64))
    }

    @Test
    fun macOsPrefersDownloadedFramework() {
        touch("lib/libSDL3.0.dylib")
        touch("lib/SDL3.framework/Versions/A/SDL3")
        touch("lib/SDL3.framework/Versions/A/Resources/Info.plist")
        touch("lib/SDL3.framework/Versions/A/Headers/SDL.h")
        assertEquals(
            listOf("SDL3.framework/Versions/A/SDL3", "SDL3.framework/Versions/A/Resources/Info.plist"),
            shipped(KlyphTargets.MACOS_ARM64),
        )
    }

    @Test
    fun linuxShipsTheSoname() {
        touch("lib/libSDL3.so.0.4.18")
        Files.createSymbolicLink(File(prefix, "lib/libSDL3.so.0").toPath(), File("libSDL3.so.0.4.18").toPath())
        assertEquals(listOf("libSDL3.so.0"), shipped(KlyphTargets.LINUX_X64))
    }

    @Test
    fun debianShipsTheSonameFromTheMultiarchFolder() {
        touch("lib/x86_64-linux-gnu/libSDL3.so.0.4.18")
        Files.createSymbolicLink(File(prefix, "lib/x86_64-linux-gnu/libSDL3.so.0").toPath(), File("libSDL3.so.0.4.18").toPath())
        touch("lib/libunrelated.so")
        assertEquals(listOf("libSDL3.so.0"), shipped(KlyphTargets.LINUX_X64))
        assertEquals(File(prefix, "lib/x86_64-linux-gnu/libSDL3.so.0"), DistributionLayout.libraries(KlyphTargets.LINUX_X64, prefix)!!.single().first)
        assertEquals(listOf("lib/x86_64-linux-gnu/libSDL3.so.0"), DistributionLayout.libraryPatterns(KlyphTargets.LINUX_X64, prefix))
    }

    @Test
    fun fedoraShipsTheSonameFromLib64() {
        touch("lib64/libSDL3.so.0")
        assertEquals(listOf("libSDL3.so.0"), shipped(KlyphTargets.LINUX_ARM64))
        assertEquals(listOf("lib64/libSDL3.so.0"), DistributionLayout.libraryPatterns(KlyphTargets.LINUX_ARM64, prefix))
    }

    @Test
    fun patternsOfAnEmptyPrefixPointAtLib() {
        assertEquals(listOf("lib/libSDL3.so.0"), DistributionLayout.libraryPatterns(KlyphTargets.LINUX_X64, prefix))
        assertEquals("lib/libSDL3.0.dylib", DistributionLayout.libraryPatterns(KlyphTargets.MACOS_ARM64, prefix).first())
        assertEquals(listOf("bin/SDL3.dll"), DistributionLayout.libraryPatterns(KlyphTargets.MINGW_X64, prefix))
    }

    @Test
    fun windowsShipsTheDll() {
        touch("bin/SDL3.dll")
        touch("lib/libSDL3.dll.a")
        assertEquals(listOf("SDL3.dll"), shipped(KlyphTargets.MINGW_X64))
    }

    @Test
    fun emptyPrefixShipsNothing() {
        assertNull(DistributionLayout.libraries(KlyphTargets.MACOS_ARM64, prefix))
        assertNull(DistributionLayout.libraries(KlyphTargets.LINUX_X64, prefix))
        assertNull(DistributionLayout.libraries(KlyphTargets.MINGW_X64, prefix))
    }

    @Test
    fun otoolOutputParsing() {
        val otoolL = """
            build/bin/macosArm64/releaseExecutable/game.kexe:
            	/opt/homebrew/opt/sdl3/lib/libSDL3.0.dylib (compatibility version 401.0.0, current version 401.18.0)
            	/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1351.0.0)
        """.trimIndent()
        val libraries = MacExecutable.linkedLibraries(otoolL)
        assertEquals(listOf("/opt/homebrew/opt/sdl3/lib/libSDL3.0.dylib", "/usr/lib/libSystem.B.dylib"), libraries)
        assertEquals("/opt/homebrew/opt/sdl3/lib/libSDL3.0.dylib", libraries.first { MacExecutable.isSdl3(it) })
        assertEquals("@rpath/libSDL3.0.dylib", MacExecutable.rpathName(libraries.first()))
        assertEquals("@rpath/SDL3.framework/Versions/A/SDL3", MacExecutable.rpathName("@rpath/SDL3.framework/Versions/A/SDL3"))
        assertFalse(MacExecutable.isSdl3("/usr/lib/libSystem.B.dylib"))

        val otooll = """
            Load command 20
                      cmd LC_RPATH
                  cmdsize 32
                     path @executable_path (offset 12)
            Load command 21
                      cmd LC_RPATH
                  cmdsize 48
                     path /opt/homebrew/opt/sdl3/lib (offset 12)
        """.trimIndent()
        assertEquals(listOf("@executable_path", "/opt/homebrew/opt/sdl3/lib"), MacExecutable.rpaths(otooll))
    }

    private fun touch(path: String) {
        val file = File(prefix, path)
        file.parentFile.mkdirs()
        assertTrue(file.createNewFile())
    }

    private fun shipped(target: String): List<String> =
        DistributionLayout.libraries(target, prefix).orEmpty().map { it.second }
}
