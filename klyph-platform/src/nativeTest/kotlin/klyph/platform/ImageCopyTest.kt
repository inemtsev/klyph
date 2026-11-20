package klyph.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageCopyTest {
    @Test
    fun copyFromWritesAHalfTransparentTexelExactly() {
        val source = Image(3, 2)
        val texel = Color(0x12345680u)
        source[1, 1] = texel
        source[2, 1] = Color.TRANSPARENT
        val target = Image(4, 4)
        target.fill(Color.WHITE)
        target.copyFrom(source, x = 2, y = 1, sourceX = 1, sourceY = 1, sourceWidth = 2, sourceHeight = 1)
        assertEquals(texel, target[2, 1], "A half-transparent texel replaces the one under it, not blended.")
        assertEquals(Color.TRANSPARENT, target[3, 1], "A transparent texel clears the one under it.")
        assertEquals(Color.WHITE, target[1, 1])
        assertEquals(Color.WHITE, target[2, 2])
    }

    @Test
    fun copyFromAnImageIntoItselfReadsItAsItWas() {
        val image = Image(4, 4)
        for (row in 0 until 4) for (column in 0 until 4) image[column, row] = Color((column * 16 + row + 1).toUInt() shl 8 or 0xFFu)
        val before = Image(4, 4)
        before.copyFrom(image, x = 0, y = 0)
        val expected = Image(4, 4)
        expected.copyFrom(image, x = 0, y = 0)
        for (row in 0 until 3) {
            for (column in 0 until 3) expected[column + 1, row + 1] = before[column, row]
        }
        image.copyFrom(image, x = 1, y = 1, sourceWidth = 3, sourceHeight = 3)
        assertContentEquals(expected.pixels, image.pixels)
    }

    @Test
    fun copyFromRejectsARectangleOutsideEitherImage() {
        val source = Image(2, 2)
        val target = Image(4, 4)
        assertEquals(
            "Destination rectangle of 2x2 at (3, 3) runs past the image size 4x4.",
            assertFailsWith<IllegalArgumentException> { target.copyFrom(source, x = 3, y = 3) }.message,
        )
        assertEquals(
            "Source rectangle of 2x1 at (1, 0) runs past the image size 2x2.",
            assertFailsWith<IllegalArgumentException> {
                target.copyFrom(source, x = 0, y = 0, sourceX = 1, sourceWidth = 2, sourceHeight = 1)
            }.message,
        )
        assertFailsWith<IllegalArgumentException> { target.copyFrom(source, x = -1, y = 0) }
        assertFailsWith<IllegalArgumentException> { target.copyFrom(source, x = 0, y = 0, sourceY = -1) }
        assertFailsWith<IllegalArgumentException> { target.copyFrom(source, x = 0, y = 0, sourceX = 3) }
        assertFailsWith<IllegalArgumentException> { target.copyFrom(source, x = Int.MAX_VALUE, y = 0) }
    }
}
