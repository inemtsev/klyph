package klyph.app

import klyph.audio.AudioConfig
import klyph.platform.Color
import klyph.platform.GpuValidation
import klyph.platform.PresentMode
import klyph.platform.Scancode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

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
        assertNotEquals(AppConfig(), AppConfig(audio = AudioConfig(enabled = false)))
        assertNotEquals(AppConfig(), AppConfig(collisionCellSize = 32f))
        assertEquals(AppConfig(audio = AudioConfig(maxVoices = 8)), AppConfig(audio = AudioConfig(maxVoices = 8)))
        assertEquals(AppConfig(audio = AudioConfig(maxVoices = 8)).hashCode(), AppConfig(audio = AudioConfig(maxVoices = 8)).hashCode())
        assertNotEquals(AppConfig(), AppConfig(overlayKey = null))
        assertNotEquals(AppConfig(), AppConfig(overlayKey = Scancode.F1))
        assertEquals(AppConfig(overlayKey = null), AppConfig(overlayKey = null))
        assertEquals(AppConfig(overlayKey = null).hashCode(), AppConfig(overlayKey = null).hashCode())
    }

    @Test
    fun toStringListsEverySetting() {
        assertEquals(
            "AppConfig(title=klyph, width=1280, height=720, fixedRate=60, maxFixedStepsPerFrame=4, " +
                "presentMode=VSYNC, target=WINDOW, gpuSync=NONE, validation=DISABLED, clearColor=Color(#1a1f2eff), assetRoot=null, " +
                "audio=AudioConfig(enabled=true, maxVoices=32, sampleRate=48000, bufferFrames=1024), collisionCellSize=64.0, overlayKey=${Scancode.F3})",
            AppConfig().toString(),
        )
    }
}
