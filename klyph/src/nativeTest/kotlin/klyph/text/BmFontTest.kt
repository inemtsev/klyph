package klyph.text

import klyph.render.Texture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A BMFont text file with two pages, a glyph on each, an empty space glyph, '?' and two kerning pairs. */
internal val TEST_FNT: String = """
    info face="Test Font" size=16 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1
    common lineHeight=16 base=12 scaleW=64 scaleH=64 pages=2 packed=0
    page id=0 file="test_0.png"
    page id=1 file="sub dir/test_1.png"
    chars count=4
    char id=65   x=0  y=0  width=8 height=10 xoffset=1 yoffset=2 xadvance=9 page=0 chnl=15
    char id=86   x=10 y=4  width=8 height=10 xoffset=0 yoffset=2 xadvance=8 page=1 chnl=15
    char id=32   x=0  y=0  width=0 height=0  xoffset=0 yoffset=0 xadvance=4 page=0 chnl=15
    char id=63   x=20 y=0  width=6 height=10 xoffset=0 yoffset=2 xadvance=7 page=0 chnl=15
    kernings count=2
    kerning first=65 second=86 amount=-2
    kerning first=86 second=65 amount=-1
""".trimIndent()

/** Parses [TEST_FNT] over two headless 64 by 64 pages. */
internal fun testFont(): Font {
    val pages = listOf(Texture(64, 64), Texture(64, 64))
    var next = 0
    return parseBmFont(TEST_FNT, "fonts/test.fnt") { pages[next++] }
}

class BmFontTest {
    @Test
    fun readsCommonPagesGlyphsAndKernings() {
        val pages = listOf(Texture(64, 64), Texture(64, 64))
        val loaded = ArrayList<String>()
        val font = parseBmFont(TEST_FNT, "fonts/test.fnt") { file ->
            loaded += file
            pages[loaded.size - 1]
        }

        assertEquals(listOf("test_0.png", "sub dir/test_1.png"), loaded)
        assertEquals(16f, font.lineHeight)
        assertEquals(12f, font.baseline)
        assertEquals(2, font.pageCount)
        assertEquals(4, font.glyphCount)
        assertEquals(2, font.kerningCount)

        val a = assertNotNull(font.glyphOrNull('A'))
        val aRegion = assertNotNull(a.region)
        assertSame(pages[0], aRegion.texture)
        assertEquals(listOf(0, 0, 8, 10), listOf(aRegion.x, aRegion.y, aRegion.width, aRegion.height))
        assertEquals(listOf(1f, 2f, 9f), listOf(a.xOffset, a.yOffset, a.xAdvance))

        val v = assertNotNull(font.glyphOrNull('V'))
        assertSame(pages[1], v.region?.texture)
        assertEquals(10, v.region?.x)
        assertEquals(4, v.region?.y)

        val space = assertNotNull(font.glyphOrNull(' '))
        assertNull(space.region)
        assertEquals(4f, space.xAdvance)

        assertNull(font.glyphOrNull('Z'))
        assertEquals(-2f, font.kerningOf('A', 'V'))
        assertEquals(-1f, font.kerningOf('V', 'A'))
        assertEquals(0f, font.kerningOf('A', 'A'))
        assertEquals("Font(glyphs=4, kernings=2, lineHeight=16.0, baseline=12.0, pages=2)", font.toString())
    }

    @Test
    fun charactersBeyondAsciiAreFound() {
        val text = """
            common lineHeight=10 base=8
            page id=0 file="p.png"
            char id=233 x=0 y=0 width=4 height=4 xoffset=0 yoffset=0 xadvance=5 page=0
            char id=1046 x=4 y=0 width=4 height=4 xoffset=0 yoffset=0 xadvance=6 page=0
            char id=128512 x=8 y=0 width=4 height=4 xoffset=0 yoffset=0 xadvance=6 page=0
            kerning first=233 second=1046 amount=3
        """.trimIndent()
        val font = parseBmFont(text, "f.fnt") { Texture(16, 16) }
        assertEquals(5f, font.glyphOrNull('é')?.xAdvance)
        assertEquals(6f, font.glyphOrNull('Ж')?.xAdvance)
        assertEquals(3f, font.kerningOf('é', 'Ж'))
        // U+1F600 does not fit in a Char and is skipped.
        assertEquals(2, font.glyphCount)
    }

    @Test
    fun binaryFileIsRejectedNamingThePath() {
        val error = assertFailsWith<FontException> {
            parseBmFont("BMF\u0003\u0001", "fonts/binary.fnt") { Texture(1, 1) }
        }
        assertTrue("fonts/binary.fnt" in error.message!!, error.message)
        assertTrue("binary" in error.message!!, error.message)
    }

    @Test
    fun malformedFilesAreRejectedBeforeAnyPageLoads() {
        var loads = 0
        val loader = { _: String ->
            loads++
            Texture(64, 64)
        }
        val missingCommon = "page id=0 file=\"a.png\"\nchar id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0"
        assertFailsWith<FontException> { parseBmFont(missingCommon, "a.fnt", loader) }

        val badNumber = "common lineHeight=ten base=8\npage id=0 file=\"a.png\""
        val error = assertFailsWith<FontException> { parseBmFont(badNumber, "a.fnt", loader) }
        assertEquals("Font file 'a.fnt' line 1 has 'ten' for 'lineHeight', which is not a number.", error.message)

        val missingKey = "common lineHeight=10 base=8\npage id=0 file=\"a.png\"\nchar id=65 x=0 y=0 width=1 xoffset=0 yoffset=0 xadvance=1"
        assertFailsWith<FontException> { parseBmFont(missingKey, "a.fnt", loader) }

        val undeclaredPage = "common lineHeight=10 base=8\npage id=0 file=\"a.png\"\n" +
            "char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=1"
        assertFailsWith<FontException> { parseBmFont(undeclaredPage, "a.fnt", loader) }

        val pageGap = "common lineHeight=10 base=8\npage id=1 file=\"a.png\""
        assertFailsWith<FontException> { parseBmFont(pageGap, "a.fnt", loader) }
        assertEquals(0, loads)
    }

    @Test
    fun glyphOutsideItsPageIsAFontException() {
        val text = "common lineHeight=10 base=8\npage id=0 file=\"a.png\"\n" +
            "char id=65 x=60 y=0 width=8 height=8 xoffset=0 yoffset=0 xadvance=8 page=0"
        val error = assertFailsWith<FontException> { parseBmFont(text, "a.fnt") { Texture(64, 64) } }
        assertTrue(error.cause is IllegalArgumentException)
    }
}
