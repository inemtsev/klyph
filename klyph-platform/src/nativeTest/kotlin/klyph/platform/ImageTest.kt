package klyph.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageTest {
    @Test
    fun startsTransparentBlack() {
        val image = Image(3, 2)
        assertEquals(Color(0x00000000u), image[2, 1])
    }

    @Test
    fun getReturnsWhatSetStored() {
        val image = Image(4, 3)
        image[3, 2] = Color(0x11223344u)
        image[0, 0] = Color(0xFFFFFFFFu)
        assertEquals(Color(0x11223344u), image[3, 2])
        assertEquals(Color(0xFFFFFFFFu), image[0, 0])
        assertEquals(Color(0x00000000u), image[1, 0])
    }

    @Test
    fun storesRowMajorRgba() {
        val image = Image(2, 2)
        image[1, 0] = Color(0x01020304u)
        assertEquals(listOf<Byte>(1, 2, 3, 4), image.pixels.slice(4..7))
    }

    @Test
    fun fillSetsEveryPixel() {
        val image = Image(5, 4)
        image.fill(Color(0x80FF00C0u))
        for (y in 0..<4) for (x in 0..<5) assertEquals(Color(0x80FF00C0u), image[x, y])
    }

    @Test
    fun outOfRangeCoordinatesThrow() {
        val image = Image(2, 3)
        assertFailsWith<IndexOutOfBoundsException> { image[2, 0] }
        assertFailsWith<IndexOutOfBoundsException> { image[0, 3] }
        assertFailsWith<IndexOutOfBoundsException> { image[-1, 0] }
        val e = assertFailsWith<IndexOutOfBoundsException> { image[0, -1] = Color.WHITE }
        assertEquals("Pixel (0, -1) is out of bounds for image size 2x3.", e.message)
    }

    @Test
    fun nonPositiveSizeThrows() {
        assertFailsWith<IllegalArgumentException> { Image(0, 1) }
        assertFailsWith<IllegalArgumentException> { Image(1, -1) }
        assertFailsWith<IllegalArgumentException> { Image(65536, 65536) }
    }

    @Test
    fun toStringShowsSize() {
        assertEquals("Image(64x32)", Image(64, 32).toString())
    }

    @Test
    fun decodesPng() {
        val image = Image.decode(TWO_BY_TWO_PNG)
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertEquals(Color(0xFF0000FFu), image[0, 0])
        assertEquals(Color(0x00FF00FFu), image[1, 0])
        assertEquals(Color(0x0000FFFFu), image[0, 1])
        assertEquals(Color(0xFFFFFF80u), image[1, 1])
    }

    @Test
    fun decodeOrNullReturnsNullOnGarbage() {
        assertNull(Image.decodeOrNull(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertNull(Image.decodeOrNull(byteArrayOf()))
        // A valid signature with the rest cut off.
        assertNull(Image.decodeOrNull(TWO_BY_TWO_PNG.copyOf(40)))
    }

    @Test
    fun decodeThrowsOnGarbage() {
        val e = assertFailsWith<ImageException> { Image.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        val message = e.message.orEmpty()
        assertTrue(message.startsWith("Image data could not be decoded: "), message)
        assertTrue(message.endsWith("."), message)
        assertFailsWith<ImageException> { Image.decode(byteArrayOf()) }
    }

    private companion object {
        /** A 2x2 RGBA PNG: red, green on the top row; blue, half-transparent white below. */
        val TWO_BY_TWO_PNG = byteArrayOf(
            -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 2, 0, 0, 0, 2,
            8, 6, 0, 0, 0, 114, -74, 13, 36, 0, 0, 0, 19, 73, 68, 65, 84, 120, -38, 99, -8, -49,
            -64, -16, 31, 12, -127, 52, 8, 52, 0, 0, 73, 73, 9, 120, -100, 81, 23, -110, 0, 0, 0,
            0, 73, 69, 78, 68, -82, 66, 96, -126,
        )
    }
}
