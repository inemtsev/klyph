package samples.app

import klyph.app.AppConfig
import klyph.app.Screenshot
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith

class Screenshots {
    @Sample
    fun screenshot() {
        // Captures the 120th frame, two seconds in at 60 frames per second.
        val config = AppConfig(screenshot = Screenshot(frame = 120, path = "title.png"))
        assertPrints(config.screenshot, "Screenshot(frame=120, path=title.png)")
        assertFailsWith<IllegalArgumentException> { Screenshot(frame = 0, path = "title.png") }
    }
}
