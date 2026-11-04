package klyph.platform

import sdl3.SDL_RemovePath
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

    @Test
    fun saveWritesPngThatDecodesToTheSameImage() {
        val image = Image(5, 3)
        image.fill(Color(0x10203040u))
        image[0, 0] = Color(0xFF0000FFu)
        image[4, 2] = Color(0x00000000u)
        image[2, 1] = Color(0x12345678u)
        val path = Files.basePath + "klyph-image-test.png"
        try {
            image.save(path)
            val decoded = Image.decode(Files.readBytes(path))
            assertEquals(image.width, decoded.width)
            assertEquals(image.height, decoded.height)
            assertEquals(image.pixels.toList(), decoded.pixels.toList())
        } finally {
            SDL_RemovePath(path)
        }
    }

    @Test
    fun encodePngStartsWithSignature() {
        val png = Image(2, 2).encodePng()
        assertEquals(listOf<Byte>(-119, 80, 78, 71, 13, 10, 26, 10), png.take(8))
    }

    @Test
    fun saveCreatesMissingDirectory() {
        val directory = Files.basePath + "klyph-image-test-dir"
        val path = "$directory/image.png"
        SDL_RemovePath(path)
        SDL_RemovePath(directory)
        try {
            Image(1, 1).save(path)
            assertEquals(1, Image.decode(Files.readBytes(path)).width)
        } finally {
            SDL_RemovePath(path)
            SDL_RemovePath(directory)
        }
    }

    @Test
    fun saveThrowsWhenParentIsAFile() {
        val file = Files.basePath + "klyph-image-test-file"
        try {
            Files.writeBytes(file, byteArrayOf(1))
            assertFailsWith<FileException> { Image(1, 1).save("$file/image.png") }
        } finally {
            SDL_RemovePath(file)
        }
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

    @Test
    fun fillCircleIsSymmetricAndSoftAtTheEdge() {
        val image = Image(16, 16)
        image.fillCircle(centerX = 8f, centerY = 8f, radius = 6.5f, color = Color.WHITE)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val pixel = image[x, y]
                assertEquals(pixel, image[15 - x, y], "Mirrored left to right at ($x, $y).")
                assertEquals(pixel, image[x, 15 - y], "Mirrored top to bottom at ($x, $y).")
                assertEquals(pixel, image[y, x], "Mirrored across the diagonal at ($x, $y).")
            }
        }
        assertEquals(Color.WHITE, image[8, 8], "The middle is fully covered.")
        assertEquals(Color(0x00000000u), image[0, 0], "The corner is outside.")
        val edge = image[13, 8]
        // Pixel 13's centre is 5.5 from the middle, a pixel inside the edge: fully covered.
        assertEquals(Color.WHITE, edge)
        val rim = image[14, 8]
        // Pixel 14's centre lies on the edge, so it is half covered, and on a transparent image keeps white.
        assertEquals(0xFFFFFF00u, rim.rgba and 0xFFFFFF00u)
        assertTrue(rim.alpha in 0.45f..0.55f, "Rim alpha ${rim.alpha} is not about half.")
    }

    @Test
    fun fillCircleBlendsItsEdgeOverWhatIsThere() {
        val image = Image(8, 8)
        image.fill(Color(0x000000FFu))
        image.fillCircle(centerX = 4f, centerY = 4f, radius = 3.5f, color = Color(0xFF0000FFu))
        assertEquals(Color(0xFF0000FFu), image[4, 4])
        val rim = image[7, 4]
        // Half covered: half red over opaque black, still opaque.
        assertEquals(0xFFu, rim.rgba and 0xFFu)
        assertTrue(rim.red in 0.45f..0.55f, "Rim red ${rim.red} is not about half.")
        assertEquals(Color(0x000000FFu), image[0, 0])
    }

    @Test
    fun fillCircleClipsToTheImageAndRejectsBadRadii() {
        val image = Image(4, 4)
        image.fillCircle(centerX = 0f, centerY = 0f, radius = 10f, color = Color.WHITE)
        assertEquals(Color.WHITE, image[3, 3])
        image.fillCircle(centerX = 100f, centerY = 100f, radius = 0f, color = Color.WHITE)
        assertFailsWith<IllegalArgumentException> { image.fillCircle(centerX = 0f, centerY = 0f, radius = -1f, color = Color.WHITE) }
        assertFailsWith<IllegalArgumentException> { image.fillCircle(centerX = Float.NaN, centerY = 0f, radius = 1f, color = Color.WHITE) }
    }
}
