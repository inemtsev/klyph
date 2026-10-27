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
}
