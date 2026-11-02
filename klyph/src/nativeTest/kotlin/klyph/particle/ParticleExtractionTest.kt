package klyph.particle

import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.render.DrawSpace
import klyph.render.QuadBuffer
import klyph.render.SPRITE_RECORD_FLOATS
import klyph.render.Texture
import klyph.render.region
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParticleExtractionTest {
    private val texture = Texture(32, 32)
    private val region = texture.region(x = 0, y = 0, width = 8, height = 4)

    /** Returns floats [from] until [until] of quad [index]'s record. */
    private fun QuadBuffer.floats(index: Int, from: Int, until: Int): List<Float> =
        (from until until).map { records[index * SPRITE_RECORD_FLOATS + it] }

    private fun assertNear(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertTrue(abs(expected[i] - actual[i]) < 1e-4f, "Expected $expected, got $actual.")
    }

    @Test
    fun oneWorldQuadPerLiveParticleAtTheEmittersLayer() {
        val world = World()
        val emitter = ParticleEmitter(region).apply {
            speed = 0f..0f
            lifetime = 10f..10f
            startRotation = 90f..90f
            layer = 4
        }
        val entity = world.create()
        world.add(entity, Transform, Transform(x = 12f, y = 34f))
        world.add(entity, ParticleEmitter, emitter)
        emitter.emit(3)
        world.advanceParticles(0f)
        val quads = QuadBuffer()

        assertEquals(3, world.extractParticles(quads))
        assertEquals(3, quads.count)
        assertNear(listOf(12f, 34f, (PI / 2).toFloat()), quads.floats(0, 0, 3))
        assertEquals(listOf(8f, 4f), quads.floats(0, 4, 6))
        assertEquals(listOf(region.u0, region.v0, region.u1, region.v1), quads.floats(0, 8, 12))
        assertEquals(4, quads.layers[0])
        assertSame(DrawSpace.WORLD, quads.spaceAt(0))
        assertSame(texture, quads.textureAt(0))
    }

    @Test
    fun colorAndScaleBlendToTheMidpointAtMidLife() {
        val world = World()
        val emitter = ParticleEmitter(region).apply {
            speed = 0f..0f
            lifetime = 1f..1f
            startColor = Color(red = 1f, green = 0f, blue = 0.2f, alpha = 1f)
            endColor = Color(red = 0f, green = 1f, blue = 0.6f, alpha = 0f)
            startScale = 1f
            endScale = 3f
        }
        val entity = world.create()
        world.add(entity, Transform, Transform())
        world.add(entity, ParticleEmitter, emitter)
        emitter.emit(1)
        world.advanceParticles(0f)
        world.advanceParticles(0.5f)
        val quads = QuadBuffer()

        world.extractParticles(quads)
        assertNear(listOf(16f, 8f), quads.floats(0, 4, 6))
        val start = emitter.startColor
        val end = emitter.endColor
        assertNear(
            listOf((start.red + end.red) / 2, (start.green + end.green) / 2, (start.blue + end.blue) / 2, 0.5f),
            quads.floats(0, 12, 16),
        )
    }

    @Test
    fun manyParticlesGrowTheBufferAfterQuadsAlreadyInIt() {
        val world = World()
        val emitter = ParticleEmitter(region, maxParticles = 100).apply { lifetime = 10f..10f }
        val entity = world.create()
        world.add(entity, Transform, Transform(x = 7f, y = 9f))
        world.add(entity, ParticleEmitter, emitter)
        emitter.emit(100)
        world.advanceParticles(0f)
        val quads = QuadBuffer(initialCapacity = 4)
        quads.add(Texture(2, 2).region(0, 0, 2, 2), 1f, 2f, 2f, 2f, 0f, Color.WHITE, 0, DrawSpace.SCREEN)

        assertEquals(100, world.extractParticles(quads))
        assertEquals(101, quads.count)
        assertEquals(listOf(1f, 2f), quads.floats(0, 0, 2))
        assertSame(DrawSpace.SCREEN, quads.spaceAt(0))
        assertEquals(listOf(7f, 9f), quads.floats(100, 0, 2))
        assertSame(texture, quads.textureAt(100))
        assertSame(DrawSpace.WORLD, quads.spaceAt(100))
    }

    @Test
    fun hiddenEmittersAndEmittersWithoutTransformAddNothing() {
        val world = World()
        val hidden = ParticleEmitter(region).apply { isVisible = false }
        val shown = world.create()
        world.add(shown, Transform, Transform())
        world.add(shown, ParticleEmitter, hidden)
        hidden.emit(5)
        world.advanceParticles(0f)
        assertEquals(5, hidden.count, "A hidden emitter still emits.")

        val loose = ParticleEmitter(region)
        loose.emit(5)
        world.add(world.create(), ParticleEmitter, loose)
        val quads = QuadBuffer()
        assertEquals(0, world.extractParticles(quads))
        assertEquals(0, quads.count)
    }
}
