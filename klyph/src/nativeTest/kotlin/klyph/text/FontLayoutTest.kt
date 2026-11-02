package klyph.text

import klyph.math.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

class FontLayoutTest {
    /** Returns every glyph [Font.layout] emits as (left, top) pairs. */
    private fun Font.positions(text: String, scale: Float = 1f, align: TextAlign = TextAlign.LEFT): List<Pair<Float, Float>> {
        val positions = ArrayList<Pair<Float, Float>>()
        layout(text, scale, align) { _, left, top -> positions += left to top }
        return positions
    }

    private fun assertSize(expected: Vec2, actual: Vec2) {
        assertEquals(expected.x, actual.x)
        assertEquals(expected.y, actual.y)
    }

    @Test
    fun measureAddsAdvancesAndKerning() {
        val font = testFont()
        // A advances 9, kerning A->V is -2, V advances 8.
        assertSize(Vec2(15f, 16f), font.measure("AV"))
        assertSize(Vec2(16f, 16f), font.measure("VA"))
        assertSize(Vec2(30f, 32f), font.measure("AV", scale = 2f))
    }

    @Test
    fun measureTakesTheWidestLineAndCountsEveryLine() {
        val font = testFont()
        assertSize(Vec2(15f, 48f), font.measure("A\nAV\n"))
        assertSize(Vec2(0f, 16f), font.measure(""))
        // Carriage returns are ignored.
        assertSize(Vec2(9f, 32f), font.measure("A\r\nA"))
    }

    @Test
    fun layoutPlacesGlyphsWithOffsetsKerningAndLines() {
        val font = testFont()
        // A at pen 0 (+1, +2); space at pen 9 draws nothing; V at pen 13 (+0, +2); next line A at pen 0.
        assertEquals(listOf(1f to 2f, 13f to 2f, 1f to 18f), font.positions("A V\nA"))
        // Kerning pulls V in by 2.
        assertEquals(listOf(1f to 2f, 7f to 2f), font.positions("AV"))
        assertEquals(listOf(2f to 4f, 14f to 4f), font.positions("AV", scale = 2f))
    }

    @Test
    fun alignmentOffsetsEachLineOnItsOwn() {
        val font = testFont()
        // Line widths: "A" = 9, "AV" = 15.
        assertEquals(listOf(-3.5f to 2f, -6.5f to 18f, -0.5f to 18f), font.positions("A\nAV", align = TextAlign.CENTER))
        assertEquals(listOf(-8f to 2f, -14f to 18f, -8f to 18f), font.positions("A\nAV", align = TextAlign.RIGHT))
    }

    @Test
    fun unknownCharactersUseTheQuestionMarkGlyph() {
        val font = testFont()
        // Z is unknown and drawn as '?', which advances 7; no kerning pairs involve '?'.
        assertSize(Vec2(16f, 16f), font.measure("ZA"))
        assertEquals(listOf(0f to 2f, 8f to 2f), font.positions("ZA"))
    }

    @Test
    fun unknownCharactersAreSkippedWithoutAQuestionMarkGlyph() {
        val font = Font(listOf(Glyph('a', null, 0f, 0f, 5f)), emptyMap(), lineHeight = 10f, baseline = 8f, pages = emptyList())
        assertSize(Vec2(10f, 10f), font.measure("a#a"))
    }
}
