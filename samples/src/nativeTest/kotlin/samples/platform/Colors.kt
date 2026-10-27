package samples.platform

import klyph.platform.Color
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith

class Colors {
    @Sample
    fun color() {
        val orange = Color(red = 1f, green = 0.5f, blue = 0f)
        assertPrints(orange, "Color(#ff8000ff)")
        assertPrints(orange.red, "1.0")
        assertPrints(orange.alpha, "1.0")
        assertPrints(Color.WHITE, "Color(#ffffffff)")
        assertFailsWith<IllegalArgumentException> { Color(red = 2f, green = 0f, blue = 0f) }
    }
}
