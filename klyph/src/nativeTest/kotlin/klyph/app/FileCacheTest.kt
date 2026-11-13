package klyph.app

import klyph.platform.SamplerAddress
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
        cache.put("/assets/ship.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP, smooth)
        cache.put("/assets/ship.png", SamplerFilter.NEAREST, SamplerAddress.CLAMP, crisp)
        assertSame(smooth, cache.getOrNull("/assets/ship.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP))
        assertSame(crisp, cache.getOrNull("/assets/ship.png", SamplerFilter.NEAREST, SamplerAddress.CLAMP))
        assertNotSame(
            cache.getOrNull("/assets/ship.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP),
            cache.getOrNull("/assets/ship.png", SamplerFilter.NEAREST, SamplerAddress.CLAMP),
        )
        assertEquals(2, cache.size)
    }

    @Test
    fun onePathWithTwoAddressesHoldsTwoTextures() {
        val cache = FileCache<Texture>()
        val clamped = Texture(8, 8)
        val tiled = Texture(8, 8, address = SamplerAddress.REPEAT)
        cache.put("/assets/ground.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP, clamped)
        cache.put("/assets/ground.png", SamplerFilter.LINEAR, SamplerAddress.REPEAT, tiled)
        assertSame(clamped, cache.getOrNull("/assets/ground.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP))
        assertSame(tiled, cache.getOrNull("/assets/ground.png", SamplerFilter.LINEAR, SamplerAddress.REPEAT))
        assertEquals(2, cache.size)
    }

    @Test
    fun everyFilterAndAddressPairIsItsOwnKey() {
        val cache = FileCache<Texture>()
        val held = HashMap<Pair<SamplerFilter, SamplerAddress>, Texture>()
        for (filter in SamplerFilter.entries) {
            for (address in SamplerAddress.entries) {
                val texture = Texture(8, 8, filter, address)
                held[filter to address] = texture
                cache.put("tile.png", filter, address, texture)
            }
        }
        assertEquals(4, cache.size)
        for ((key, texture) in held) assertSame(texture, cache.getOrNull("tile.png", key.first, key.second))
    }

    @Test
    fun missingPathOrFilterIsNull() {
        val cache = FileCache<Texture>()
        cache.put("/assets/ship.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP, Texture(8, 8))
        assertNull(cache.getOrNull("/assets/ship.png", SamplerFilter.NEAREST, SamplerAddress.CLAMP))
        assertNull(cache.getOrNull("/assets/ship.png", SamplerFilter.LINEAR, SamplerAddress.REPEAT))
        assertNull(cache.getOrNull("/assets/enemy.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP))
    }

    @Test
    fun clearForgetsEverything() {
        val cache = FileCache<Texture>()
        cache.put("a.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP, Texture(8, 8))
        cache.put("a.png", SamplerFilter.NEAREST, SamplerAddress.CLAMP, Texture(8, 8, SamplerFilter.NEAREST))
        assertEquals("FileCache(size=2)", cache.toString())
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.getOrNull("a.png", SamplerFilter.LINEAR, SamplerAddress.CLAMP))
    }
}
