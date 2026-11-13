package klyph.platform

import sdl3.SDL_GPUSamplerAddressMode
import kotlin.test.Test
import kotlin.test.assertEquals

// Creating a sampler needs a GPU device, which tests cannot open, so these check the mapping that
// GpuDevice.createSampler hands to SDL.
class SamplerAddressTest {
    @Test
    fun clampMapsToClampToEdge() {
        assertEquals(SDL_GPUSamplerAddressMode.SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE, SamplerAddress.CLAMP.raw)
    }

    @Test
    fun repeatMapsToRepeat() {
        assertEquals(SDL_GPUSamplerAddressMode.SDL_GPU_SAMPLERADDRESSMODE_REPEAT, SamplerAddress.REPEAT.raw)
    }

    @Test
    fun toStringIsTheName() {
        assertEquals(listOf("CLAMP", "REPEAT"), SamplerAddress.entries.map { it.toString() })
    }
}
