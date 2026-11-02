package samples.app

import klyph.app.AppConfig
import klyph.app.GpuSync
import klyph.app.RenderTarget
import klyph.platform.Scancode
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith

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
        assertFailsWith<IllegalArgumentException> { AppConfig(width = 0) }
    }
}
