package samples.app

import klyph.app.AppConfig
import klyph.app.GpuSync
import klyph.app.RenderTarget
import klyph.platform.SamplerFilter
import klyph.platform.Scancode
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class AppConfigs {
    @Sample
    fun appConfig() {
        val config = AppConfig(title = "Shooter", fixedRate = 50, target = RenderTarget.OFFSCREEN, gpuSync = GpuSync.WAIT_EACH_FRAME)
        assertPrints(config.title, "Shooter")
        assertPrints(config.width, "1280")
        assertPrints(config.fixedRate, "50")
        assertPrints(config.overlayKey == Scancode.F3, "true")
        // No key toggles the debug overlay; the game sets app.overlay.isVisible itself.
        assertPrints(AppConfig(overlayKey = null).overlayKey, "null")
        // Textures load with linear sampling; a pixel-art game sets nearest sampling once for all of them.
        assertPrints(config.textureFilter, "LINEAR")
        assertPrints(AppConfig(textureFilter = SamplerFilter.NEAREST).textureFilter, "NEAREST")
        // Frames last as long as they took; a frame delta gives every frame the same length, so runs repeat.
        assertPrints(config.frameDelta, "null")
        assertPrints(AppConfig(frameDelta = 16.milliseconds).frameDelta, "16ms")
        assertFailsWith<IllegalArgumentException> { AppConfig(width = 0) }
    }

    @Sample
    fun fromEnvironment() {
        // KLYPH_FRAMES=600 in the environment would quit after 600 frames; the title stays the game's.
        val config = AppConfig.fromEnvironment(AppConfig(title = "Shooter"))
        assertPrints(config.title, "Shooter")
    }
}
