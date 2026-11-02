package klyph.app

import klyph.platform.SamplerFilter
import klyph.render.Texture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class FileCacheTest {
    @Test
    fun onePathWithTwoFiltersHoldsTwoTextures() {
        val cache = FileCache<Texture>()
        val smooth = Texture(8, 8, SamplerFilter.LINEAR)
        val crisp = Texture(8, 8, SamplerFilter.NEAREST)
        cache.put("/assets/ship.png", SamplerFilter.LINEAR, smooth)
        cache.put("/assets/ship.png", SamplerFilter.NEAREST, crisp)
        assertSame(smooth, cache.getOrNull("/assets/ship.png", SamplerFilter.LINEAR))
        assertSame(crisp, cache.getOrNull("/assets/ship.png", SamplerFilter.NEAREST))
        assertNotSame(cache.getOrNull("/assets/ship.png", SamplerFilter.LINEAR), cache.getOrNull("/assets/ship.png", SamplerFilter.NEAREST))
        assertEquals(2, cache.size)
    }

    @Test
    fun missingPathOrFilterIsNull() {
        val cache = FileCache<Texture>()
        cache.put("/assets/ship.png", SamplerFilter.LINEAR, Texture(8, 8))
        assertNull(cache.getOrNull("/assets/ship.png", SamplerFilter.NEAREST))
        assertNull(cache.getOrNull("/assets/enemy.png", SamplerFilter.LINEAR))
    }

    @Test
    fun clearForgetsEverything() {
        val cache = FileCache<Texture>()
        cache.put("a.png", SamplerFilter.LINEAR, Texture(8, 8))
        cache.put("a.png", SamplerFilter.NEAREST, Texture(8, 8, SamplerFilter.NEAREST))
        assertEquals("FileCache(size=2)", cache.toString())
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.getOrNull("a.png", SamplerFilter.LINEAR))
    }
}
