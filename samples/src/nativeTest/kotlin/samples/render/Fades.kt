package samples.render

import klyph.render.Fade
import klyph.render.FadeEnd
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Fades {
    @Sample
    fun fade() {
        // Keeps the sprite as it is for a second, fades it out over half a second, then destroys it.
        val fade = Fade(500.milliseconds, end = FadeEnd.DESTROY, delay = 1.seconds)
        assertPrints(fade, "Fade(elapsed=0.00s, duration=500ms, delay=1s, target=0.0, end=DESTROY)")
        assertPrints(fade.isFinished, "false")
        assertFailsWith<IllegalArgumentException> { Fade(1.seconds, target = 1.5f) }
    }
}
