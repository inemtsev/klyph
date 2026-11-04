package samples.platform

import klyph.platform.Color
import klyph.platform.lerp
import klyph.platform.withAlpha
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

    @Sample
    fun constants() {
        assertPrints(Color.BLACK, "Color(#000000ff)")
        assertPrints(Color.RED, "Color(#ff0000ff)")
        assertPrints(Color.YELLOW.green, "1.0")
        assertPrints(Color.GRAY, "Color(#808080ff)")
        assertPrints(Color.CYAN.withAlpha(0.5f), "Color(#00ffff80)")
    }

    @Sample
    fun withAlpha() {
        val orange = Color(red = 1f, green = 0.5f, blue = 0f)
        assertPrints(orange.withAlpha(0f), "Color(#ff800000)")
        assertPrints(Color.TRANSPARENT, "Color(#00000000)")
    }

    @Sample
    fun lerp() {
        assertPrints(Color.BLACK.lerp(Color.WHITE, 0.5f), "Color(#808080ff)")
        assertPrints(Color.BLACK.lerp(Color.WHITE, 2f), "Color(#ffffffff)")
    }
}
