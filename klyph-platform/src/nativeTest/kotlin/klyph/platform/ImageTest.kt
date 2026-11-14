package klyph.platform

import sdl3.SDL_RemovePath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    @Test
    fun fillRectSetsExactlyTheRectangle() {
        val image = Image(6, 5)
        image.fillRect(x = 1, y = 2, width = 3, height = 2, color = Color(0x11223344u))
        for (y in 0 until 5) {
            for (x in 0 until 6) {
                val expected = if (x in 1..3 && y in 2..3) Color(0x11223344u) else Color.TRANSPARENT
                assertEquals(expected, image[x, y], "Pixel ($x, $y).")
            }
        }
    }

    @Test
    fun fillRectSetsPixelsWithoutBlending() {
        val image = Image(2, 1)
        image.fill(Color.WHITE)
        image.fillRect(x = 0, y = 0, width = 1, height = 1, color = Color(0x10203040u))
        assertEquals(Color(0x10203040u), image[0, 0], "Alpha is stored as it is, not blended.")
        image.fillRect(x = 1, y = 0, width = 1, height = 1, color = Color.TRANSPARENT)
        assertEquals(Color.TRANSPARENT, image[1, 0], "A transparent color clears the rectangle.")
    }

    @Test
    fun fillRectMatchesSettingEachPixelInsideTheImage() {
        val rectangles = listOf(
            Rectangle(0, 0, 6, 5), Rectangle(1, 2, 3, 2), Rectangle(-2, -1, 4, 3), Rectangle(4, 3, 10, 10), Rectangle(-3, -3, 20, 20),
            Rectangle(6, 0, 3, 3), Rectangle(0, 5, 3, 3), Rectangle(-5, 0, 5, 5), Rectangle(2, 2, 0, 3), Rectangle(2, 2, 3, 0),
            Rectangle(-1, -1, 1, 1), Rectangle(5, 4, 1, 1), Rectangle(-1, 4, 8, 1),
        )
        for (rectangle in rectangles) {
            val expected = Image(6, 5)
            for (row in rectangle.y until rectangle.y + rectangle.height) {
                for (column in rectangle.x until rectangle.x + rectangle.width) {
                    if (column in 0 until 6 && row in 0 until 5) expected[column, row] = Color(0xAABBCCDDu)
                }
            }
            val actual = Image(6, 5)
            actual.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, Color(0xAABBCCDDu))
            assertContentEquals(expected.pixels, actual.pixels, "Rectangle $rectangle.")
        }
    }

    @Test
    fun fillRectWithHugeValuesDoesNotOverflow() {
        val image = Image(4, 3)
        image.fillRect(x = Int.MAX_VALUE, y = 0, width = Int.MAX_VALUE, height = 1, color = Color.WHITE)
        image.fillRect(x = 0, y = Int.MAX_VALUE, width = 1, height = Int.MAX_VALUE, color = Color.WHITE)
        image.fillRect(x = Int.MIN_VALUE, y = Int.MIN_VALUE, width = Int.MAX_VALUE, height = Int.MAX_VALUE, color = Color.WHITE)
        assertContentEquals(ByteArray(4 * 3 * 4), image.pixels, "Rectangles far outside paint nothing.")
        image.fillRect(x = -1, y = -1, width = Int.MAX_VALUE, height = Int.MAX_VALUE, color = Color.WHITE)
        for (y in 0 until 3) for (x in 0 until 4) assertEquals(Color.WHITE, image[x, y], "Pixel ($x, $y).")
    }

    @Test
    fun fillRectWithZeroSizePaintsNothingAndNegativeSizeThrows() {
        val image = Image(3, 3)
        image.fillRect(x = 1, y = 1, width = 0, height = 2, color = Color.WHITE)
        image.fillRect(x = 1, y = 1, width = 2, height = 0, color = Color.WHITE)
        assertContentEquals(ByteArray(3 * 3 * 4), image.pixels)
        val width = assertFailsWith<IllegalArgumentException> { image.fillRect(x = 0, y = 0, width = -1, height = 1, color = Color.WHITE) }
        assertEquals("Rectangle width -1 is negative.", width.message)
        val height = assertFailsWith<IllegalArgumentException> { image.fillRect(x = 0, y = 0, width = 1, height = -2, color = Color.WHITE) }
        assertEquals("Rectangle height -2 is negative.", height.message)
        assertContentEquals(ByteArray(3 * 3 * 4), image.pixels, "A rejected rectangle paints nothing.")
    }

    @Test
    fun drawCopiesOpaquePixelsToTheOffset() {
        val source = patterned(2, 2)
        val target = Image(4, 4)
        target.draw(source, x = 1, y = 2)
        for (y in 0 until 2) for (x in 0 until 2) assertEquals(source[x, y], target[x + 1, y + 2], "Source pixel ($x, $y).")
        assertEquals(Color.TRANSPARENT, target[0, 0])
        assertEquals(Color.TRANSPARENT, target[0, 2])
        assertEquals(Color.TRANSPARENT, target[3, 3])
        assertEquals(Color.TRANSPARENT, target[1, 1])
    }

    @Test
    fun drawLeavesWhatIsUnderTransparentPixels() {
        val source = Image(1, 1)
        source[0, 0] = Color(0xFF000000u)
        val white = Image(1, 1)
        white.fill(Color.WHITE)
        white.draw(source, x = 0, y = 0)
        assertEquals(Color.WHITE, white[0, 0])
        val clear = Image(1, 1)
        clear.draw(source, x = 0, y = 0)
        assertEquals(Color.TRANSPARENT, clear[0, 0], "The color of a fully transparent pixel is not copied.")
    }

    @Test
    fun drawBlendsPartlyTransparentPixelsOverAnOpaqueOne() {
        val source = Image(1, 1)
        source[0, 0] = Color(0xFF000080u)
        val target = Image(1, 1)
        target.fill(Color(0x0000FFFFu))
        target.draw(source, x = 0, y = 0)
        val result = target[0, 0].rgba
        // Half red over blue: about half of each, and still opaque.
        assertTrue(channelsWithin(result, 0x80007FFFu, 1), "Result #${result.toString(16)} is not about #80007fff.")
    }

    @Test
    fun drawCountsBothAlphasInTheResult() {
        val source = Image(1, 1)
        source[0, 0] = Color(0x0000FF80u)
        val target = Image(1, 1)
        target[0, 0] = Color(0xFF000080u)
        target.draw(source, x = 0, y = 0)
        val result = target[0, 0].rgba
        // Alpha 0.5 over alpha 0.5 leaves 0.75, two thirds of it from the blue on top.
        assertTrue(channelsWithin(result, 0x5500AAC0u, 1), "Result #${result.toString(16)} is not about #5500aac0.")
    }

    @Test
    fun drawOverATransparentPixelKeepsEveryAlphaExactly() {
        for (alpha in 1..255) {
            val color = Color(0xC86432_00u or alpha.toUInt())
            val source = Image(1, 1)
            source[0, 0] = color
            val target = Image(1, 1)
            target.draw(source, x = 0, y = 0)
            assertEquals(color, target[0, 0], "Alpha $alpha.")
        }
    }

    @Test
    fun drawClipsToTheImage() {
        val source = patterned(3, 3)
        val offsets = listOf(
            0 to 0, 1 to 1, -1 to -2, -2 to 0, 2 to 2, 3 to 3, 4 to 0, 0 to 4, -3 to -3, 5 to -5, -2 to 3, 2 to -1,
        )
        for ((x, y) in offsets) {
            val expected = patterned(4, 3)
            val actual = patterned(4, 3)
            for (row in 0 until 3) {
                for (column in 0 until 3) {
                    if (x + column in 0 until 4 && y + row in 0 until 3) expected[x + column, y + row] = source[column, row]
                }
            }
            actual.draw(source, x, y)
            assertContentEquals(expected.pixels, actual.pixels, "Drawn at ($x, $y).")
        }
    }

    @Test
    fun drawAnImageLargerThanTheTargetFillsItWithTheTopLeftOfTheSource() {
        val source = patterned(5, 5)
        val target = Image(2, 2)
        target.draw(source, x = 0, y = 0)
        for (y in 0 until 2) for (x in 0 until 2) assertEquals(source[x, y], target[x, y], "Pixel ($x, $y).")
    }

    @Test
    fun drawWithHugeOffsetsDoesNotOverflow() {
        val source = patterned(2, 2)
        val target = Image(3, 3)
        for (x in listOf(Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE - 1, Int.MIN_VALUE + 1)) {
            target.draw(source, x = x, y = 0)
            target.draw(source, x = 0, y = x)
        }
        assertContentEquals(ByteArray(3 * 3 * 4), target.pixels, "Nothing of the source lies inside the target.")
    }

    @Test
    fun drawLeavesTheSourceUnchanged() {
        val source = patterned(3, 3)
        source[1, 1] = Color(0x12345680u)
        val before = source.pixels.copyOf()
        val target = Image(2, 2)
        target.fill(Color.WHITE)
        target.draw(source, x = -1, y = -1)
        assertContentEquals(before, source.pixels)
    }

    @Test
    fun drawAnImageIntoItselfReadsItAsItWas() {
        for ((x, y) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1)) {
            val image = patterned(4, 4)
            val before = patterned(4, 4)
            val expected = patterned(4, 4)
            for (row in 0 until 4) {
                for (column in 0 until 4) {
                    if (x + column in 0 until 4 && y + row in 0 until 4) expected[x + column, y + row] = before[column, row]
                }
            }
            image.draw(image, x, y)
            assertContentEquals(expected.pixels, image.pixels, "Drawn into itself at ($x, $y).")
        }
    }

    @Test
    fun drawAnImageIntoItselfMatchesDrawingACopyWhenTheSourceStartsAboveOrOverlapsOnlyTheBottom() {
        for (y in listOf(-3, 3)) {
            val image = patterned(4, 4)
            val expected = patterned(4, 4)
            expected.draw(patterned(4, 4), x = 1, y = y)
            image.draw(image, x = 1, y = y)
            assertContentEquals(expected.pixels, image.pixels, "Drawn into itself at (1, $y).")
        }
    }

    /** The rectangle of [width] by [height] pixels whose top-left corner is at ([x], [y]). */
    private class Rectangle(val x: Int, val y: Int, val width: Int, val height: Int) {
        override fun toString(): String = "${width}x$height at ($x, $y)"
    }

    /** Returns a [width] by [height] image of opaque pixels that all differ, for sizes up to 6 by 6. */
    private fun patterned(width: Int, height: Int): Image {
        val image = Image(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val red = (x * 40 + 10).toUInt()
                val green = (y * 40 + 10).toUInt()
                val blue = ((x + y) * 20 + 5).toUInt()
                image[x, y] = Color((red shl 24) or (green shl 16) or (blue shl 8) or 0xFFu)
            }
        }
        return image
    }

    /** Returns whether each of the four bytes of [actual] is at most [tolerance] away from the same byte of [expected]. */
    private fun channelsWithin(actual: UInt, expected: UInt, tolerance: Int): Boolean =
        (0 until 4).all { index ->
            val shift = index * 8
            abs(((actual shr shift) and 0xFFu).toInt() - ((expected shr shift) and 0xFFu).toInt()) <= tolerance
        }
}
