package klyph.app

import klyph.platform.Color
import klyph.platform.Image
import klyph.render.Texture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DiscCacheTest {
    @Test
    fun drawsEachDiameterOnceWithAPixelOfPadding() {
        val uploaded = ArrayList<Image>()
        val cache = DiscCache { image ->
            uploaded += image
            Texture(image.width, image.height)
        }
        val small = cache.get(8)
        assertSame(small, cache.get(8), "A second call returns the cached region.")
        assertEquals(1, uploaded.size)
        val large = cache.get(16)
        assertNotSame(small.texture, large.texture)
        assertEquals(2, uploaded.size)
        assertEquals(2, cache.size)

        assertEquals(listOf(1, 1, 8, 8), listOf(small.x, small.y, small.width, small.height))
        assertEquals(10, small.texture.width, "One transparent texel on each side.")
        val image = uploaded[0]
        for (i in 0 until 10) {
            assertEquals(0f, image[i, 0].alpha, "The top padding row is transparent at $i.")
            assertEquals(0f, image[0, i].alpha, "The left padding column is transparent at $i.")
        }
        assertEquals(Color.WHITE, image[5, 5])
    }

    @Test
    fun rejectsDiametersOutOfRange() {
        val cache = DiscCache { image -> Texture(image.width, image.height) }
        assertFailsWith<IllegalArgumentException> { cache.get(0) }
        val error = assertFailsWith<IllegalArgumentException> { cache.get(5000) }
        assertEquals("Disc diameter 5000 is not between 1 and 4096.", error.message)
    }

    @Test
    fun discImageIsSymmetric() {
        val image = DiscCache.discImage(7)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) assertEquals(image[x, y], image[image.width - 1 - x, image.height - 1 - y])
        }
    }
}
