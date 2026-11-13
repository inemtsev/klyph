package klyph.render

import klyph.platform.SamplerAddress
import klyph.platform.SamplerFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class TextureAddressTest {
    @Test
    fun textureClampsByDefault() {
        val texture = Texture(16, 16)
        assertEquals(SamplerAddress.CLAMP, texture.address)
        assertEquals("Texture(16x16)", texture.toString())
    }

    @Test
    fun textureKeepsTheAddressItWasCreatedWith() {
        val texture = Texture(32, 32, address = SamplerAddress.REPEAT)
        assertEquals(SamplerAddress.REPEAT, texture.address)
        assertEquals(SamplerFilter.LINEAR, texture.filter)
        assertEquals("Texture(32x32, REPEAT)", texture.toString())
    }

    @Test
    fun toStringShowsTheFilterBeforeTheAddressAndOnlyWhenTheyDiffer() {
        assertEquals("Texture(32x32, NEAREST, REPEAT)", Texture(32, 32, SamplerFilter.NEAREST, SamplerAddress.REPEAT).toString())
        assertEquals("Texture(32x32, NEAREST)", Texture(32, 32, SamplerFilter.NEAREST, SamplerAddress.CLAMP).toString())
        assertEquals("Texture(32x32)", Texture(32, 32, SamplerFilter.LINEAR, SamplerAddress.CLAMP).toString())
    }
}
