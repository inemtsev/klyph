package klyph.app

import klyph.platform.Color
import klyph.platform.GpuValidation
import klyph.platform.PresentMode
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
    }

    @Test
    fun toStringListsEverySetting() {
        assertEquals(
            "AppConfig(title=klyph, width=1280, height=720, fixedRate=60, maxFixedStepsPerFrame=4, " +
                "presentMode=VSYNC, target=WINDOW, gpuSync=NONE, validation=DISABLED, clearColor=Color(#1a1f2eff))",
            AppConfig().toString(),
        )
    }
}
