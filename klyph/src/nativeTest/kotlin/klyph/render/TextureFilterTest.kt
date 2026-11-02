package klyph.render

import klyph.platform.SamplerFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class TextureFilterTest {
    @Test
    fun textureIsLinearByDefault() {
        val texture = Texture(16, 16)
        assertEquals(SamplerFilter.LINEAR, texture.filter)
        assertEquals("Texture(16x16)", texture.toString())
    }

    @Test
    fun textureKeepsTheFilterItWasCreatedWith() {
        val texture = Texture(160, 60, SamplerFilter.NEAREST)
        assertEquals(SamplerFilter.NEAREST, texture.filter)
        assertEquals("Texture(160x60, NEAREST)", texture.toString())
    }

    @Test
    fun regionsAndRunsKeepTheirTexturesFilter() {
        val crisp = Texture(8, 8, SamplerFilter.NEAREST)
        val smooth = Texture(8, 8)
        val order = SpriteOrder()
        order.begin()
        order.add(0, crisp.asRegion().texture)
        order.add(0, smooth)
        order.finish()
        assertEquals(2, order.runCount)
        assertEquals(SamplerFilter.NEAREST, order.runTexture(0).filter)
        assertEquals(SamplerFilter.LINEAR, order.runTexture(1).filter)
    }
}
