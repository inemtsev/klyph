package klyph.text

import klyph.platform.Color
import klyph.render.Texture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class BuiltInFontTest {
    private val atlas = Texture(BuiltInFont.ATLAS_WIDTH, BuiltInFont.ATLAS_HEIGHT)
    private val builtIn = BuiltInFont.over(atlas)
    private val image = BuiltInFont.rasterize()

    @Test
    fun coversPrintableAsciiWithEightByEightCells() {
        val font = builtIn.font
        assertEquals(95, font.glyphCount)
        assertEquals(8f, font.lineHeight)
        assertEquals(7f, font.baseline)
        assertEquals(1, font.pageCount)
        assertEquals(0, font.kerningCount)
        for (code in 0x20..0x7E) {
            val glyph = assertNotNull(font.glyphOrNull(code.toChar()))
            assertEquals(8f, glyph.xAdvance)
        }
        assertNull(font.glyphOrNull('\u007F'))
        assertNull(font.glyphOrNull(' ')?.region)
        assertEquals(16f, font.measure("AB").x)
        assertEquals(8f, font.measure("AB").y)
    }

    @Test
    fun letterAIsRasterisedFromItsRows() {
        val region = assertNotNull(builtIn.font.glyphOrNull('A')?.region)
        assertSame(atlas, region.texture)
        assertEquals(8, region.width)
        // Rows 0C 1E 33 33 3F 33 33 00, least significant bit leftmost.
        var set = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) if (image[region.x + x, region.y + y] == Color.WHITE) set++
        }
        assertEquals(28, set)
        assertEquals(Color.WHITE, image[region.x + 2, region.y])
        assertEquals(Color.WHITE, image[region.x + 3, region.y])
        assertEquals(0f, image[region.x + 1, region.y].alpha)
        assertEquals(0f, image[region.x + 4, region.y].alpha)
        // The padding around a glyph is transparent white, so filtering does not darken edges.
        for (y in 0 until 8) assertEquals(Color(0xFFFFFF00u), image[region.x - 1, region.y + y])
    }

    @Test
    fun whitePixelIsSurroundedByWhite() {
        val pixel = builtIn.whitePixel
        assertSame(atlas, pixel.texture)
        assertEquals(1, pixel.width)
        assertEquals(1, pixel.height)
        for (dy in -1..1) {
            for (dx in -1..1) assertEquals(Color.WHITE, image[pixel.x + dx, pixel.y + dy])
        }
    }
}
