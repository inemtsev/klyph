package samples.math

import klyph.math.degrees
import klyph.math.toDegrees
import samples.Sample
import samples.assertPrints
import kotlin.math.PI

class Angles {
    @Sample
    fun degrees() {
        assertPrints(90f.degrees == (PI / 2).toFloat(), "true")
        assertPrints(PI.toFloat().toDegrees(), "180.0")
    }
}
