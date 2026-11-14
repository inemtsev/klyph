package klyph.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextureRegionTest {
    @Test
    fun textureRejectsNonPositiveSize() {
        assertFailsWith<IllegalArgumentException> { Texture(0, 16) }
        assertFailsWith<IllegalArgumentException> { Texture(16, -1) }
    }

    @Test
    fun testTextureHasNoHandleAndClosesTwice() {
        val texture = Texture(256, 128)
        assertFailsWith<IllegalStateException> { texture.handle }
        texture.close()
        texture.close()
        assertEquals("Texture(256x128)", texture.toString())
    }

    @Test
    fun uvsAreEdgesOverTextureSize() {
        val region = Texture(256, 128).region(x = 64, y = 32, width = 32, height = 64)
        assertEquals(0.25f, region.u0)
        assertEquals(0.25f, region.v0)
        assertEquals(0.375f, region.u1)
        assertEquals(0.75f, region.v1)
    }

    @Test
    fun asRegionCoversTheWholeTexture() {
        val texture = Texture(64, 32)
        val region = texture.asRegion()
        assertEquals(0, region.x)
        assertEquals(0, region.y)
        assertEquals(64, region.width)
        assertEquals(32, region.height)
        assertEquals(0f, region.u0)
        assertEquals(0f, region.v0)
        assertEquals(1f, region.u1)
        assertEquals(1f, region.v1)
    }

    @Test
    fun regionTouchingTheFarEdgesFits() {
        val region = Texture(64, 32).region(x = 48, y = 16, width = 16, height = 16)
        assertEquals(1f, region.u1)
        assertEquals(1f, region.v1)
    }

    @Test
    fun rejectsRegionsOutsideTheTexture() {
        val texture = Texture(64, 32)
        assertFailsWith<IllegalArgumentException> { texture.region(x = -1, y = 0, width = 8, height = 8) }
        assertFailsWith<IllegalArgumentException> { texture.region(x = 0, y = -1, width = 8, height = 8) }
        assertFailsWith<IllegalArgumentException> { texture.region(x = 57, y = 0, width = 8, height = 8) }
        assertFailsWith<IllegalArgumentException> { texture.region(x = 0, y = 25, width = 8, height = 8) }
        assertFailsWith<IllegalArgumentException> { texture.region(x = 0, y = 0, width = 0, height = 8) }
        assertFailsWith<IllegalArgumentException> { texture.region(x = 0, y = 0, width = 8, height = -8) }
        assertFailsWith<IllegalArgumentException> { texture.region(x = Int.MAX_VALUE, y = 0, width = 8, height = 8) }
    }

    @Test
    fun aspectRatioIsWidthOverHeight() {
        val texture = Texture(256, 128)
        assertEquals(2f, texture.asRegion().aspectRatio)
        assertEquals(0.5f, texture.region(x = 0, y = 0, width = 32, height = 64).aspectRatio)
        assertEquals(1f, texture.region(x = 8, y = 8, width = 24, height = 24).aspectRatio)
    }

    @Test
    fun aspectRatioGivesTheWidthAtAHeight() {
        val region = Texture(64, 64).region(x = 0, y = 0, width = 24, height = 16)
        assertEquals(36f, 24f * region.aspectRatio)
        assertEquals(16f, 24f / region.aspectRatio)
    }

    @Test
    fun aspectRatioStaysPositiveAndFiniteForOneTexelStrips() {
        val wide = Texture(4096, 1).asRegion().aspectRatio
        val tall = Texture(1, 4096).asRegion().aspectRatio
        assertEquals(4096f, wide)
        assertEquals(1f / 4096f, tall)
        assertTrue(wide.isFinite() && tall > 0f)
    }

    @Test
    fun equalityNeedsTheSameTextureInstance() {
        val texture = Texture(64, 32)
        assertEquals(texture.region(x = 0, y = 0, width = 8, height = 8), texture.region(x = 0, y = 0, width = 8, height = 8))
        assertEquals(texture.region(x = 0, y = 0, width = 8, height = 8).hashCode(), texture.region(x = 0, y = 0, width = 8, height = 8).hashCode())
        assertNotEquals(texture.region(x = 0, y = 0, width = 8, height = 8), texture.region(x = 8, y = 0, width = 8, height = 8))
        assertNotEquals(texture.region(x = 0, y = 0, width = 8, height = 8), Texture(64, 32).region(x = 0, y = 0, width = 8, height = 8))
    }

    @Test
    fun onlyTheWholeTextureIsTheWholeTexture() {
        val texture = Texture(16, 8)
        assertTrue(texture.asRegion().isWholeTexture)
        assertTrue(texture.region(x = 0, y = 0, width = 16, height = 8).isWholeTexture)
        assertFalse(texture.region(x = 0, y = 0, width = 16, height = 4).isWholeTexture, "Half the height.")
        assertFalse(texture.region(x = 0, y = 0, width = 8, height = 8).isWholeTexture, "Half the width.")
        assertFalse(texture.region(x = 8, y = 0, width = 8, height = 8).isWholeTexture, "The right half.")
        assertFalse(texture.region(x = 0, y = 4, width = 16, height = 4).isWholeTexture, "The bottom half.")
    }

    @Test
    fun toStringShowsRectangleAndTexture() {
        val region = Texture(256, 256).region(x = 0, y = 16, width = 32, height = 8)
        assertEquals("TextureRegion(x=0, y=16, size=32x8, texture=Texture(256x256))", region.toString())
    }

    @Test
    fun spriteSizeDefaultsToTheRegion() {
        val sprite = Sprite(Texture(64, 64).region(x = 0, y = 0, width = 16, height = 32))
        assertEquals(16f, sprite.width)
        assertEquals(32f, sprite.height)
        assertEquals(
            "Sprite(size=16.0x32.0, color=Color(#ffffffff), layer=0, flippedX=false, flippedY=false, visible=true, " +
                "region=TextureRegion(x=0, y=0, size=16x32, texture=Texture(64x64)))",
            sprite.toString(),
        )
    }
}
