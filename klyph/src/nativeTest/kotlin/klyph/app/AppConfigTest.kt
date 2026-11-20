package klyph.app

import klyph.audio.AudioConfig
import klyph.audio.AudioOutput
import klyph.platform.Color
import klyph.platform.CursorMode
import klyph.platform.GpuValidation
import klyph.platform.PresentMode
import klyph.platform.SamplerFilter
import klyph.platform.Scancode
import klyph.platform.WindowMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class AppConfigTest {
    @Test
    fun defaults() {
        val config = AppConfig()
        assertEquals("klyph", config.title)
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
        assertEquals(60, config.fixedRate)
        assertEquals(4, config.maxFixedStepsPerFrame)
        assertEquals(PresentMode.VSYNC, config.presentMode)
        assertEquals(RenderTarget.WINDOW, config.target)
        assertEquals(GpuSync.NONE, config.gpuSync)
        assertEquals(GpuValidation.DISABLED, config.validation)
        assertEquals(Color(red = 0.10f, green = 0.12f, blue = 0.18f), config.clearColor)
        assertEquals(null, config.assetRoot)
        assertEquals(64f, config.collisionCellSize)
        assertEquals(Scancode.F3, config.overlayKey)
        assertEquals(WindowMode.WINDOWED, config.windowMode)
        assertEquals(CursorMode.VISIBLE, config.cursor)
        assertEquals(null, config.screenshot)
        assertEquals(SamplerFilter.LINEAR, config.textureFilter)
        assertEquals(null, config.frameDelta)
        assertEquals(TextureAtlas.AUTOMATIC, config.atlas)
    }

    @Test
    fun textureFilterIsSetByName() {
        assertEquals(SamplerFilter.NEAREST, AppConfig(textureFilter = SamplerFilter.NEAREST).textureFilter)
        assertEquals(SamplerFilter.LINEAR, AppConfig(textureFilter = SamplerFilter.LINEAR).textureFilter)
    }

    @Test
    fun frameDeltaIsAboveZeroAndNoLongerThanTheLongestFrame() {
        assertEquals(16.milliseconds, AppConfig(frameDelta = 16.milliseconds).frameDelta)
        assertEquals(1.milliseconds, AppConfig(frameDelta = 1.milliseconds).frameDelta)
        // Four steps at 10 a second last 400 ms, so here 250 ms, the longest frame Time accepts, is the limit.
        assertEquals(250.milliseconds, AppConfig(fixedRate = 10, frameDelta = 250.milliseconds).frameDelta)
        for (delta in listOf(Duration.ZERO, (-1).milliseconds, 251.milliseconds, Duration.INFINITE)) {
            assertFailsWith<IllegalArgumentException>(delta.toString()) { AppConfig(fixedRate = 10, frameDelta = delta) }
        }
        val error = assertFailsWith<IllegalArgumentException> { AppConfig(fixedRate = 10, frameDelta = 300.milliseconds) }
        assertEquals("Frame delta 300ms is not above zero and at most 250ms.", error.message)
    }

    @Test
    fun frameDeltaIsAtMostTheFixedStepsOneFrameRuns() {
        // The loop runs at most maxFixedStepsPerFrame steps of Time.fixedDelta in a frame and drops the rest of a longer one.
        val step = Time(fixedRate = 60).fixedDelta
        val longest = step * 4
        assertEquals(longest, AppConfig(frameDelta = longest).frameDelta, "The four steps of the defaults are allowed.")
        for (delta in listOf(longest + 1.nanoseconds, longest + step)) {
            assertFailsWith<IllegalArgumentException>(delta.toString()) { AppConfig(frameDelta = delta) }
        }
        val error = assertFailsWith<IllegalArgumentException> { AppConfig(frameDelta = 100.milliseconds) }
        assertEquals(
            "Frame delta 100ms is longer than 66.666664ms, the most that 4 fixed steps at a fixed rate of 60 cover.",
            error.message,
        )
        // Fewer steps or a lower rate move the limit: two steps of 20 ms are 40 ms.
        assertEquals(40.milliseconds, AppConfig(fixedRate = 50, maxFixedStepsPerFrame = 2, frameDelta = 40.milliseconds).frameDelta)
        val two = assertFailsWith<IllegalArgumentException> {
            AppConfig(fixedRate = 50, maxFixedStepsPerFrame = 2, frameDelta = 41.milliseconds)
        }
        assertEquals("Frame delta 41ms is longer than 40ms, the most that 2 fixed steps at a fixed rate of 50 cover.", two.message)
    }

    @Test
    fun rejectsNonPositiveCollisionCellSize() {
        assertFailsWith<IllegalArgumentException> { AppConfig(collisionCellSize = 0f) }
        assertFailsWith<IllegalArgumentException> { AppConfig(collisionCellSize = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { AppConfig(collisionCellSize = Float.POSITIVE_INFINITY) }
        assertEquals(8f, AppConfig(collisionCellSize = 8f).collisionCellSize)
    }

    @Test
    fun rejectsNonPositiveSize() {
        assertFailsWith<IllegalArgumentException> { AppConfig(width = 0) }
        assertFailsWith<IllegalArgumentException> { AppConfig(height = -1) }
    }

    @Test
    fun rejectsFixedRateOutsideRange() {
        assertFailsWith<IllegalArgumentException> { AppConfig(fixedRate = 0) }
        assertFailsWith<IllegalArgumentException> { AppConfig(fixedRate = 1001) }
        assertEquals(1, AppConfig(fixedRate = 1).fixedRate)
        assertEquals(1000, AppConfig(fixedRate = 1000).fixedRate)
    }

    @Test
    fun rejectsZeroMaxFixedSteps() {
        assertFailsWith<IllegalArgumentException> { AppConfig(maxFixedStepsPerFrame = 0) }
        assertEquals(1, AppConfig(maxFixedStepsPerFrame = 1).maxFixedStepsPerFrame)
    }

    @Test
    fun messageNamesTheValue() {
        val error = assertFailsWith<IllegalArgumentException> { AppConfig(fixedRate = -5) }
        assertEquals("Fixed rate -5 is not between 1 and 1000.", error.message)
    }

    @Test
    fun equalityComparesEverySetting() {
        assertEquals(AppConfig(title = "a"), AppConfig(title = "a"))
        assertEquals(AppConfig(title = "a").hashCode(), AppConfig(title = "a").hashCode())
        assertNotEquals(AppConfig(), AppConfig(title = "a"))
        assertNotEquals(AppConfig(), AppConfig(width = 640))
        assertNotEquals(AppConfig(), AppConfig(height = 480))
        assertNotEquals(AppConfig(), AppConfig(fixedRate = 50))
        assertNotEquals(AppConfig(), AppConfig(maxFixedStepsPerFrame = 2))
        assertNotEquals(AppConfig(), AppConfig(presentMode = PresentMode.IMMEDIATE))
        assertNotEquals(AppConfig(), AppConfig(validation = GpuValidation.ENABLED))
        assertNotEquals(AppConfig(), AppConfig(overlay = OverlayStart.VISIBLE))
        assertNotEquals(AppConfig(), AppConfig(frameLimit = 10))
        assertNotEquals(AppConfig(), AppConfig(timeLimit = 2.seconds))
        assertNotEquals(AppConfig(), AppConfig(target = RenderTarget.OFFSCREEN))
        assertNotEquals(AppConfig(), AppConfig(gpuSync = GpuSync.WAIT_EACH_FRAME))
        assertNotEquals(AppConfig(), AppConfig(clearColor = Color.WHITE))
        assertNotEquals(AppConfig(), AppConfig(assetRoot = "data/"))
        assertNotEquals(AppConfig(), AppConfig(audio = AudioConfig(output = AudioOutput.NONE)))
        assertNotEquals(AppConfig(), AppConfig(collisionCellSize = 32f))
        assertEquals(AppConfig(audio = AudioConfig(maxVoices = 8)), AppConfig(audio = AudioConfig(maxVoices = 8)))
        assertEquals(AppConfig(audio = AudioConfig(maxVoices = 8)).hashCode(), AppConfig(audio = AudioConfig(maxVoices = 8)).hashCode())
        assertNotEquals(AppConfig(), AppConfig(overlayKey = null))
        assertNotEquals(AppConfig(), AppConfig(overlayKey = Scancode.F1))
        assertEquals(AppConfig(overlayKey = null), AppConfig(overlayKey = null))
        assertEquals(AppConfig(overlayKey = null).hashCode(), AppConfig(overlayKey = null).hashCode())
        assertNotEquals(AppConfig(), AppConfig(windowMode = WindowMode.FULLSCREEN))
        assertNotEquals(AppConfig(), AppConfig(cursor = CursorMode.HIDDEN))
        assertNotEquals(AppConfig(), AppConfig(textureFilter = SamplerFilter.NEAREST))
        assertEquals(AppConfig(textureFilter = SamplerFilter.NEAREST), AppConfig(textureFilter = SamplerFilter.NEAREST))
        assertEquals(
            AppConfig(textureFilter = SamplerFilter.NEAREST).hashCode(),
            AppConfig(textureFilter = SamplerFilter.NEAREST).hashCode(),
        )
        assertNotEquals(AppConfig(), AppConfig(screenshot = Screenshot(20, "shot.png")))
        assertNotEquals(AppConfig(screenshot = Screenshot(20, "shot.png")), AppConfig(screenshot = Screenshot(21, "shot.png")))
        assertEquals(AppConfig(screenshot = Screenshot(20, "shot.png")), AppConfig(screenshot = Screenshot(20, "shot.png")))
        assertEquals(
            AppConfig(screenshot = Screenshot(20, "shot.png")).hashCode(),
            AppConfig(screenshot = Screenshot(20, "shot.png")).hashCode(),
        )
        assertNotEquals(AppConfig(), AppConfig(frameDelta = 16.milliseconds))
        assertNotEquals(AppConfig(frameDelta = 16.milliseconds), AppConfig(frameDelta = 20.milliseconds))
        assertEquals(AppConfig(frameDelta = 16.milliseconds), AppConfig(frameDelta = 16.milliseconds))
        assertEquals(
            AppConfig(frameDelta = 16.milliseconds).hashCode(),
            AppConfig(frameDelta = 16.milliseconds).hashCode(),
        )
        assertNotEquals(AppConfig(), AppConfig(atlas = TextureAtlas.NONE))
        assertEquals(AppConfig(atlas = TextureAtlas.NONE), AppConfig(atlas = TextureAtlas.NONE))
        assertEquals(AppConfig(atlas = TextureAtlas.NONE).hashCode(), AppConfig(atlas = TextureAtlas.NONE).hashCode())
    }

    @Test
    fun toStringListsEverySetting() {
        assertEquals(
            "AppConfig(title=klyph, width=1280, height=720, fixedRate=60, maxFixedStepsPerFrame=4, " +
                "presentMode=VSYNC, target=WINDOW, gpuSync=NONE, validation=DISABLED, clearColor=Color(#1a1f2eff), assetRoot=null, " +
                "audio=AudioConfig(output=DEVICE, maxVoices=32, sampleRate=48000, bufferFrames=1024), collisionCellSize=64.0, overlayKey=${Scancode.F3}, " +
                "overlay=HIDDEN, frameLimit=null, timeLimit=null, windowMode=WINDOWED, cursor=VISIBLE, screenshot=null, " +
                "textureFilter=LINEAR, frameDelta=null, atlas=AUTOMATIC)",
            AppConfig().toString(),
        )
        val shot = AppConfig(
            screenshot = Screenshot(20, "/tmp/shot.png"),
            textureFilter = SamplerFilter.NEAREST,
            frameDelta = 16.milliseconds,
        ).toString()
        assertTrue(
            shot.endsWith("screenshot=Screenshot(frame=20, path=/tmp/shot.png), textureFilter=NEAREST, frameDelta=16ms, atlas=AUTOMATIC)"),
            shot,
        )
    }

    private fun environment(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = mapOf(*pairs)
        return { map[it] }
    }

    @Test
    fun fromEnvironmentKeepsTheBaseWithoutVariables() {
        // Every setting differs from its default, so a setting that fromEnvironment forgets to copy fails the test.
        val base = AppConfig(
            title = "Game",
            width = 640,
            height = 480,
            fixedRate = 50,
            maxFixedStepsPerFrame = 2,
            presentMode = PresentMode.IMMEDIATE,
            target = RenderTarget.OFFSCREEN,
            gpuSync = GpuSync.WAIT_EACH_FRAME,
            validation = GpuValidation.ENABLED,
            clearColor = Color.RED,
            assetRoot = "data",
            audio = AudioConfig(output = AudioOutput.NONE, maxVoices = 8),
            collisionCellSize = 32f,
            overlayKey = null,
            overlay = OverlayStart.VISIBLE,
            frameLimit = 10,
            timeLimit = 2.seconds,
            windowMode = WindowMode.FULLSCREEN,
            cursor = CursorMode.HIDDEN,
            screenshot = Screenshot(5, "base.png"),
            textureFilter = SamplerFilter.NEAREST,
            frameDelta = 20.milliseconds,
            atlas = TextureAtlas.NONE,
        )
        // A setting left out of the list above stays at its default in toString, which the loop below catches.
        val defaults = settingsOf(AppConfig())
        val changed = settingsOf(base)
        assertEquals("klyph", defaults["title"], "settingsOf splits toString into name=value settings.")
        assertTrue(defaults.getValue("audio").startsWith("AudioConfig("), "A value with parentheses stays in one piece.")
        for ((name, value) in defaults) {
            assertNotEquals(value, changed.getValue(name), "The base leaves $name at its default.")
        }
        val kept = AppConfig.fromEnvironment(base, environment())
        assertEquals(base, kept)
        assertEquals(base.toString(), kept.toString(), "equals and toString must both see every setting.")
        val variables = listOf(
            "FRAMES", "SECONDS", "FRAME_DELTA", "VALIDATION", "VSYNC", "OFFSCREEN", "OVERLAY", "FULLSCREEN", "SCREENSHOT", "ATLAS",
        )
        val empty = environment(*variables.map { "KLYPH_$it" to "" }.toTypedArray())
        assertEquals(base, AppConfig.fromEnvironment(base, empty), "An empty value counts as unset.")
    }

    /** Splits the `toString` of [config] into its top-level settings by name, so `audio=AudioConfig(...)` stays one value. */
    private fun settingsOf(config: AppConfig): Map<String, String> {
        val text = config.toString().removePrefix("AppConfig(").removeSuffix(")")
        val settings = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, char ->
            when {
                char == '(' -> depth++
                char == ')' -> depth--
                char == ',' && depth == 0 -> {
                    settings += text.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        settings += text.substring(start).trim()
        return settings.associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    @Test
    fun fromEnvironmentReadsEveryVariable() {
        val config = AppConfig.fromEnvironment(
            AppConfig(title = "Game", collisionCellSize = 32f),
            environment(
                "KLYPH_FRAMES" to "120",
                "KLYPH_SECONDS" to "2.5",
                "KLYPH_FRAME_DELTA" to "0.016",
                "KLYPH_VALIDATION" to "1",
                "KLYPH_VSYNC" to "0",
                "KLYPH_OFFSCREEN" to "1",
                "KLYPH_OVERLAY" to "1",
            ),
        )
        assertEquals("Game", config.title)
        assertEquals(32f, config.collisionCellSize)
        assertEquals(120, config.frameLimit)
        assertEquals(2500.milliseconds, config.timeLimit)
        assertEquals(16.milliseconds, config.frameDelta)
        assertEquals(GpuValidation.ENABLED, config.validation)
        assertEquals(PresentMode.IMMEDIATE, config.presentMode)
        assertEquals(RenderTarget.OFFSCREEN, config.target)
        assertEquals(GpuSync.WAIT_EACH_FRAME, config.gpuSync, "Offscreen measures frame cost, so it waits for the GPU.")
        assertEquals(OverlayStart.VISIBLE, config.overlay)
    }

    @Test
    fun fromEnvironmentTurnsSettingsOffWithZero() {
        val base = AppConfig(
            validation = GpuValidation.ENABLED,
            target = RenderTarget.OFFSCREEN,
            gpuSync = GpuSync.WAIT_EACH_FRAME,
            overlay = OverlayStart.VISIBLE,
        )
        val config = AppConfig.fromEnvironment(
            base,
            environment("KLYPH_VALIDATION" to "0", "KLYPH_VSYNC" to "1", "KLYPH_OFFSCREEN" to "0", "KLYPH_OVERLAY" to "0"),
        )
        assertEquals(GpuValidation.DISABLED, config.validation)
        assertEquals(PresentMode.VSYNC, config.presentMode)
        assertEquals(RenderTarget.WINDOW, config.target)
        assertEquals(GpuSync.WAIT_EACH_FRAME, config.gpuSync, "Turning offscreen off keeps the base's GPU sync.")
        assertEquals(OverlayStart.HIDDEN, config.overlay)
    }

    @Test
    fun fromEnvironmentReadsScreenshotAndFullscreen() {
        val config = AppConfig.fromEnvironment(
            AppConfig(),
            environment("KLYPH_SCREENSHOT" to "20:C:/shots/a.png", "KLYPH_FULLSCREEN" to "1"),
        )
        assertEquals(Screenshot(20, "C:/shots/a.png"), config.screenshot)
        assertEquals(WindowMode.FULLSCREEN, config.windowMode)
        val windowed = AppConfig.fromEnvironment(AppConfig(windowMode = WindowMode.FULLSCREEN), environment("KLYPH_FULLSCREEN" to "0"))
        assertEquals(WindowMode.WINDOWED, windowed.windowMode)
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_FULLSCREEN" to "yes")) }
    }

    @Test
    fun fromEnvironmentReadsTheAtlas() {
        assertEquals(TextureAtlas.NONE, AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_ATLAS" to "0")).atlas)
        val base = AppConfig(atlas = TextureAtlas.NONE)
        assertEquals(TextureAtlas.AUTOMATIC, AppConfig.fromEnvironment(base, environment("KLYPH_ATLAS" to "1")).atlas)
        assertEquals(TextureAtlas.NONE, AppConfig.fromEnvironment(base, environment()).atlas)
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_ATLAS" to "off")) }
    }

    @Test
    fun fromEnvironmentReadsTheFrameDeltaInSeconds() {
        val values = listOf("0.016" to 16.milliseconds, "0.25" to 250.milliseconds, "0.005" to 5.milliseconds, "1e-3" to 1.milliseconds)
        // Four steps at 10 a second last 400 ms, so every value is within what one frame runs.
        for ((value, expected) in values) {
            val config = AppConfig.fromEnvironment(AppConfig(fixedRate = 10), environment("KLYPH_FRAME_DELTA" to value))
            assertEquals(expected, config.frameDelta, value)
        }
        val base = AppConfig(frameDelta = 20.milliseconds)
        val kept = AppConfig.fromEnvironment(base, environment())
        assertEquals(20.milliseconds, kept.frameDelta, "Without the variable the base's delta stays.")
        assertEquals(16.milliseconds, AppConfig.fromEnvironment(base, environment("KLYPH_FRAME_DELTA" to "0.016")).frameDelta)
        // 0.1 is a number of seconds the variable reads, but four steps of the default rate do not cover it.
        val tooLong = environment("KLYPH_FRAME_DELTA" to "0.1")
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(AppConfig(), tooLong) }
        assertEquals(100.milliseconds, AppConfig.fromEnvironment(AppConfig(maxFixedStepsPerFrame = 8), tooLong).frameDelta)
    }

    @Test
    fun fromEnvironmentIgnoresMalformedScreenshot() {
        val base = AppConfig(screenshot = Screenshot(5, "base.png"))
        for (value in listOf("shot.png", "0:shot.png", "x:shot.png", "20:")) {
            assertEquals(Screenshot(5, "base.png"), AppConfig.fromEnvironment(base, environment("KLYPH_SCREENSHOT" to value)).screenshot, value)
        }
        assertEquals(null, AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_SCREENSHOT" to "shot.png")).screenshot)
    }

    @Test
    fun fromEnvironmentRejectsValuesItCannotRead() {
        val frames = assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_FRAMES" to "many")) }
        assertEquals("Environment variable KLYPH_FRAMES is 'many', which is not a whole number of at least 0.", frames.message)
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_FRAMES" to "-1")) }
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_SECONDS" to "soon")) }
        val flag = assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_VSYNC" to "yes")) }
        assertEquals("Environment variable KLYPH_VSYNC is 'yes', which is neither 0 nor 1.", flag.message)
        for (value in listOf("fast", "0", "-0.016", "0.3", "NaN", "Infinity", "1e-12")) {
            val delta = assertFailsWith<IllegalArgumentException>(value) {
                AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_FRAME_DELTA" to value))
            }
            assertEquals(
                "Environment variable KLYPH_FRAME_DELTA is '$value', which is not a number of seconds above 0 and at most 0.25.",
                delta.message,
            )
        }
    }

    @Test
    fun limitsAreValidatedAndCompared() {
        assertFailsWith<IllegalArgumentException> { AppConfig(frameLimit = -1) }
        assertFailsWith<IllegalArgumentException> { AppConfig(timeLimit = (-1).seconds) }
        assertEquals(0, AppConfig(frameLimit = 0).frameLimit)
        assertEquals(AppConfig(timeLimit = 5.seconds).hashCode(), AppConfig(timeLimit = 5.seconds).hashCode())
    }
}
