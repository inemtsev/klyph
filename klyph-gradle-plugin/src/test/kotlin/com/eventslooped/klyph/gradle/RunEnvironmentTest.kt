package com.eventslooped.klyph.gradle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class RunEnvironmentTest {
    private val root = File("/work/game")

    @Test
    fun gradlePropertyWinsOverShell() {
        val environment = RunEnvironment.of(
            property = mapOf("klyph.frames" to "120")::get,
            environment = mapOf("KLYPH_FRAMES" to "60", "KLYPH_SECONDS" to "5")::get,
            rootDirectory = root,
        )
        assertEquals(mapOf("KLYPH_FRAMES" to "120", "KLYPH_SECONDS" to "5"), environment)
    }

    @Test
    fun everyEngineVariablePassesThrough() {
        val names = listOf(
            "SPRITES", "CHILDREN", "TEXTURES", "INTERPOLATE", "FRAMES", "SECONDS", "OFFSCREEN",
            "VSYNC", "VALIDATION", "AUTOPILOT", "MUSIC", "CELL", "OVERLAY", "SCREENSHOT", "FULLSCREEN",
        )
        assertEquals(names, RunEnvironment.variables)
        val environment = RunEnvironment.of(property = { "1" }, environment = { null }, rootDirectory = root)
        assertEquals(names.map { "KLYPH_$it" }.toSet(), environment.keys)
    }

    @Test
    fun unrelatedVariablesStayOut() {
        val environment = RunEnvironment.of(
            property = mapOf("klyph.unknown" to "1", "sdl3.prefix" to "/opt")::get,
            environment = mapOf("KLYPH_UNKNOWN" to "1", "PATH" to "/bin")::get,
            rootDirectory = root,
        )
        assertEquals(emptyMap(), environment)
    }

    @Test
    fun musicResolvesAgainstRootDirectory() {
        val relative = RunEnvironment.of(mapOf("klyph.music" to "games/shooter/assets/music.ogg")::get, { null }, root)
        assertEquals(File("/work/game/games/shooter/assets/music.ogg").path, relative["KLYPH_MUSIC"])
        val absolute = RunEnvironment.of({ null }, mapOf("KLYPH_MUSIC" to "/tmp/song.ogg")::get, root)
        assertEquals(File("/tmp/song.ogg").path, absolute["KLYPH_MUSIC"])
    }

    @Test
    fun screenshotPathResolvesAgainstRootDirectory() {
        val relative = RunEnvironment.of(mapOf("klyph.screenshot" to "20:build/shot.png")::get, { null }, root)
        assertEquals("20:" + File("/work/game/build/shot.png").path, relative["KLYPH_SCREENSHOT"])
        val absolute = RunEnvironment.of({ null }, mapOf("KLYPH_SCREENSHOT" to "5:/tmp/shot.png")::get, root)
        assertEquals("5:" + File("/tmp/shot.png").path, absolute["KLYPH_SCREENSHOT"])
        val malformed = RunEnvironment.of({ null }, mapOf("KLYPH_SCREENSHOT" to "shot.png")::get, root)
        assertEquals("shot.png", malformed["KLYPH_SCREENSHOT"])
    }

    @Test
    fun defaultsYieldToSetValues() {
        val environment = RunEnvironment.of(
            property = mapOf("klyph.frames" to "10")::get,
            environment = { null },
            rootDirectory = root,
            defaults = mapOf("KLYPH_FRAMES" to "2000", "KLYPH_OFFSCREEN" to "1"),
        )
        assertEquals(mapOf("KLYPH_FRAMES" to "10", "KLYPH_OFFSCREEN" to "1"), environment)
    }
}
