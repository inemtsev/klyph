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
import kotlin.time.Duration.Companion.milliseconds
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
    }

    @Test
    fun textureFilterIsSetByName() {
        assertEquals(SamplerFilter.NEAREST, AppConfig(textureFilter = SamplerFilter.NEAREST).textureFilter)
        assertEquals(SamplerFilter.LINEAR, AppConfig(textureFilter = SamplerFilter.LINEAR).textureFilter)
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
    }

    @Test
    fun toStringListsEverySetting() {
        assertEquals(
            "AppConfig(title=klyph, width=1280, height=720, fixedRate=60, maxFixedStepsPerFrame=4, " +
                "presentMode=VSYNC, target=WINDOW, gpuSync=NONE, validation=DISABLED, clearColor=Color(#1a1f2eff), assetRoot=null, " +
                "audio=AudioConfig(output=DEVICE, maxVoices=32, sampleRate=48000, bufferFrames=1024), collisionCellSize=64.0, overlayKey=${Scancode.F3}, " +
                "overlay=HIDDEN, frameLimit=null, timeLimit=null, windowMode=WINDOWED, cursor=VISIBLE, screenshot=null, " +
                "textureFilter=LINEAR)",
            AppConfig().toString(),
        )
        val shot = AppConfig(screenshot = Screenshot(20, "/tmp/shot.png"), textureFilter = SamplerFilter.NEAREST).toString()
        assertTrue(shot.endsWith("screenshot=Screenshot(frame=20, path=/tmp/shot.png), textureFilter=NEAREST)"), shot)
    }

    private fun environment(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = mapOf(*pairs)
        return { map[it] }
    }

    @Test
    fun fromEnvironmentKeepsTheBaseWithoutVariables() {
        val base = AppConfig(
            title = "Game",
            frameLimit = 10,
            presentMode = PresentMode.IMMEDIATE,
            windowMode = WindowMode.FULLSCREEN,
            cursor = CursorMode.HIDDEN,
            screenshot = Screenshot(5, "base.png"),
            textureFilter = SamplerFilter.NEAREST,
        )
        assertEquals(base, AppConfig.fromEnvironment(base, environment()))
        assertEquals(base, AppConfig.fromEnvironment(base, environment("KLYPH_FRAMES" to "", "KLYPH_VSYNC" to "")), "An empty value counts as unset.")
    }

    @Test
    fun fromEnvironmentReadsEveryVariable() {
        val config = AppConfig.fromEnvironment(
            AppConfig(title = "Game", collisionCellSize = 32f),
            environment(
                "KLYPH_FRAMES" to "120",
                "KLYPH_SECONDS" to "2.5",
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
            AppConfig(cursor = CursorMode.HIDDEN),
            environment("KLYPH_SCREENSHOT" to "20:C:/shots/a.png", "KLYPH_FULLSCREEN" to "1"),
        )
        assertEquals(Screenshot(20, "C:/shots/a.png"), config.screenshot)
        assertEquals(WindowMode.FULLSCREEN, config.windowMode)
        assertEquals(CursorMode.HIDDEN, config.cursor, "The cursor has no variable, so it comes from the base.")
        val pixelArt = AppConfig.fromEnvironment(AppConfig(textureFilter = SamplerFilter.NEAREST), environment("KLYPH_FRAMES" to "1"))
        assertEquals(SamplerFilter.NEAREST, pixelArt.textureFilter, "The texture filter has no variable, so it comes from the base.")
        val windowed = AppConfig.fromEnvironment(AppConfig(windowMode = WindowMode.FULLSCREEN), environment("KLYPH_FULLSCREEN" to "0"))
        assertEquals(WindowMode.WINDOWED, windowed.windowMode)
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(AppConfig(), environment("KLYPH_FULLSCREEN" to "yes")) }
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
    }

    @Test
    fun limitsAreValidatedAndCompared() {
        assertFailsWith<IllegalArgumentException> { AppConfig(frameLimit = -1) }
        assertFailsWith<IllegalArgumentException> { AppConfig(timeLimit = (-1).seconds) }
        assertEquals(0, AppConfig(frameLimit = 0).frameLimit)
        assertNotEquals(AppConfig(), AppConfig(frameLimit = 5))
        assertNotEquals(AppConfig(), AppConfig(timeLimit = 5.seconds))
        assertNotEquals(AppConfig(), AppConfig(overlay = OverlayStart.VISIBLE))
        assertEquals(AppConfig(timeLimit = 5.seconds).hashCode(), AppConfig(timeLimit = 5.seconds).hashCode())
    }
}
