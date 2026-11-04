package klyph.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorTest {
    @Test
    fun channelRoundTrip() {
        val color = Color(1f, 0f, 0.5f, 0.2f)
        assertEquals(1f, color.red)
        assertEquals(0f, color.green)
        assertEquals(0.5f, color.blue, 1f / 255f)
        assertEquals(0.2f, color.alpha, 1f / 255f)
    }

    @Test
    fun alphaDefaultsToOpaque() {
        assertEquals(1f, Color(0.1f, 0.2f, 0.3f).alpha)
    }

    @Test
    fun packsAsRrggbbaa() {
        assertEquals(0xFF0000FFu, Color(1f, 0f, 0f).rgba)
    }

    @Test
    fun whiteIsOpaqueWhite() {
        assertEquals(Color(1f, 1f, 1f, 1f), Color.WHITE)
        assertEquals(1f, Color.WHITE.red)
        assertEquals(1f, Color.WHITE.alpha)
    }

    @Test
    fun namedColorsAreOpaqueAndMatchTheirChannels() {
        assertEquals(Color(0f, 0f, 0f), Color.BLACK)
        assertEquals(Color(1f, 0f, 0f), Color.RED)
        assertEquals(Color(0f, 1f, 0f), Color.GREEN)
        assertEquals(Color(0f, 0f, 1f), Color.BLUE)
        assertEquals(Color(1f, 1f, 0f), Color.YELLOW)
        assertEquals(Color(0f, 1f, 1f), Color.CYAN)
        assertEquals(Color(1f, 0f, 1f), Color.MAGENTA)
        assertEquals(Color.BLACK.lerp(Color.WHITE, 0.5f), Color.GRAY)
        for (color in listOf(Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.GRAY)) {
            assertEquals(1f, color.alpha, "$color")
        }
    }

    @Test
    fun outOfRangeChannelThrows() {
        assertFailsWith<IllegalArgumentException> { Color(1.1f, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { Color(0f, -0.1f, 0f) }
        assertFailsWith<IllegalArgumentException> { Color(0f, 0f, 2f) }
        assertFailsWith<IllegalArgumentException> { Color(0f, 0f, 0f, 1.5f) }
    }

    @Test
    fun toStringFormat() {
        assertEquals("Color(#ffffffff)", Color.WHITE.toString())
        assertEquals("Color(#000000ff)", Color(0f, 0f, 0f).toString())
        assertEquals("Color(#00000000)", Color(0u).toString())
    }

    @Test
    fun withAlphaReplacesOnlyTheAlphaChannel() {
        assertEquals(Color(0x11223300u), Color(0x112233FFu).withAlpha(0f))
        assertEquals(Color(0x11223380u), Color(0x112233FFu).withAlpha(0.5f))
        assertEquals(Color(0xFFFFFFFFu), Color(0xFFFFFF00u).withAlpha(1f))
        val error = assertFailsWith<IllegalArgumentException> { Color.WHITE.withAlpha(1.5f) }
        assertEquals("Alpha channel 1.5 is outside 0.0..1.0.", error.message)
    }

    @Test
    fun lerpBlendsEveryChannelAndClampsT() {
        val from = Color(0x00FF2080u)
        val to = Color(0xFF0060FFu)
        assertEquals(from, from.lerp(to, 0f))
        assertEquals(to, from.lerp(to, 1f))
        assertEquals(Color(0x808040C0u), from.lerp(to, 0.5f))
        assertEquals(from, from.lerp(to, -3f))
        assertEquals(to, from.lerp(to, 7f))
        assertEquals(from, from.lerp(to, Float.NaN))
    }

    @Test
    fun transparentIsZero() {
        assertEquals(0u, Color.TRANSPARENT.rgba)
        assertEquals(0f, Color.TRANSPARENT.alpha)
    }
}
