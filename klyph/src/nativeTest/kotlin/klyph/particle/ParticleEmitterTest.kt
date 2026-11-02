package klyph.particle

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.propagateTransforms
import klyph.render.Texture
import klyph.render.asRegion
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParticleEmitterTest {
    private val region = Texture(8, 8).asRegion()

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 1e-4f) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $expected, got $actual.")
    }

    /** Creates an entity at ([x], [y]) carrying [emitter]. */
    private fun World.emitterAt(x: Float, y: Float, emitter: ParticleEmitter): Entity {
        val entity = create()
        add(entity, Transform, Transform(x = x, y = y))
        add(entity, ParticleEmitter, emitter)
        return entity
    }

    @Test
    fun burstEmitsTheCountAtTheEntitysPosition() {
        val world = World()
        val emitter = ParticleEmitter(region)
        world.emitterAt(30f, 40f, emitter)

        emitter.emit(5)
        assertEquals(0, emitter.count, "A burst is emitted by the next advance, not by emit.")
        assertEquals(5, world.advanceParticles(1f / 60f))
        assertEquals(5, emitter.count)
        for (i in 0 until 5) {
            assertEquals(30f, emitter.xs[i])
            assertEquals(40f, emitter.ys[i])
            assertEquals(0f, emitter.ages[i])
        }
        // The burst is spent: the next advance emits nothing new.
        assertEquals(5, world.advanceParticles(1f / 60f))
    }

    @Test
    fun deadParticlesAreRemovedAfterTheirLifetime() {
        val world = World()
        val emitter = ParticleEmitter(region).apply {
            lifetime = 0.5f..1f
            random = Random(1)
        }
        world.emitterAt(0f, 0f, emitter)
        emitter.emit(20)
        world.advanceParticles(0f)
        assertEquals(20, emitter.count)

        world.advanceParticles(0.4f)
        assertEquals(20, emitter.count, "No lifetime is below 0.5 s.")
        world.advanceParticles(0.4f)
        assertTrue(emitter.count in 1 until 20, "Some of 20 lifetimes in 0.5..1 s end before 0.8 s; count ${emitter.count}.")
        for (i in 0 until emitter.count) assertTrue(emitter.ages[i] < emitter.lifetimes[i])
        world.advanceParticles(0.3f)
        assertEquals(0, emitter.count)
    }

    @Test
    fun rateAccumulatesAcrossFrames() {
        val world = World()
        val emitter = ParticleEmitter(region).apply {
            rate = 30f
            lifetime = 10f..10f
        }
        world.emitterAt(0f, 0f, emitter)
        val counts = IntArray(60)
        for (step in 0 until 60) counts[step] = world.advanceParticles(1f / 60f)
        // Half a particle a step: one every second step, the half carried in between.
        assertEquals(0, counts[0])
        assertEquals(1, counts[1])
        assertEquals(30, counts[59])
    }

    @Test
    fun gravityChangesVelocity() {
        val world = World()
        val emitter = ParticleEmitter(region).apply {
            speed = 0f..0f
            lifetime = 10f..10f
            gravityX = -20f
            gravityY = 100f
        }
        world.emitterAt(0f, 0f, emitter)
        emitter.emit(1)
        world.advanceParticles(0f)

        world.advanceParticles(0.5f)
        assertNear(-10f, emitter.velocitiesX[0])
        assertNear(50f, emitter.velocitiesY[0])
        // Velocity changes first, then moves the particle.
        assertNear(25f, emitter.ys[0])
        assertNear(-5f, emitter.xs[0])
    }

    @Test
    fun velocityFollowsDirectionAndSpeed() {
        val world = World()
        val emitter = ParticleEmitter(region).apply {
            direction = 90f
            spread = 0f
            speed = 40f..40f
            lifetime = 10f..10f
        }
        world.emitterAt(0f, 0f, emitter)
        emitter.emit(1)
        world.advanceParticles(0f)
        // 90 degrees points down the screen, +y.
        assertNear(0f, emitter.velocitiesX[0])
        assertNear(40f, emitter.velocitiesY[0])
        world.advanceParticles(0.25f)
        assertNear(10f, emitter.ys[0])
    }

    @Test
    fun clearRemovesLiveParticlesAndPendingBursts() {
        val world = World()
        val emitter = ParticleEmitter(region)
        world.emitterAt(0f, 0f, emitter)
        emitter.emit(10)
        world.advanceParticles(0f)
        emitter.emit(10)

        emitter.clear()
        assertEquals(0, emitter.count)
        assertEquals(0, world.advanceParticles(0f), "The burst requested before clear is gone too.")
    }

    @Test
    fun emitClampsAtCapacity() {
        val world = World()
        val emitter = ParticleEmitter(region, maxParticles = 4).apply { lifetime = 10f..10f }
        world.emitterAt(0f, 0f, emitter)
        assertEquals(4, emitter.capacity)

        emitter.emit(3)
        emitter.emit(Int.MAX_VALUE)
        world.advanceParticles(0f)
        assertEquals(4, emitter.count)
        emitter.emit(2)
        world.advanceParticles(0f)
        assertEquals(4, emitter.count, "A full pool emits nothing.")
    }

    @Test
    fun rateBeyondTheFreeSlotsIsDropped() {
        val world = World()
        val emitter = ParticleEmitter(region, maxParticles = 10).apply {
            rate = 1000f
            lifetime = 10f..10f
        }
        world.emitterAt(0f, 0f, emitter)
        assertEquals(10, world.advanceParticles(0.5f))
        assertEquals(10, world.advanceParticles(0.5f))
    }

    @Test
    fun seededRandomGivesIdenticalRuns() {
        fun run(seed: Int): List<FloatArray> {
            val world = World()
            val emitter = ParticleEmitter(region).apply {
                random = Random(seed)
                rate = 120f
                speed = 10f..200f
                lifetime = 0.2f..1.5f
                startRotation = 0f..360f
                angularVelocity = -90f..90f
                gravityY = 50f
            }
            world.emitterAt(5f, 5f, emitter)
            emitter.emit(30)
            repeat(90) { world.advanceParticles(1f / 60f) }
            val count = emitter.count
            return listOf(emitter.xs, emitter.ys, emitter.rotations, emitter.ages).map { it.copyOf(count) }
        }
        val first = run(7)
        val second = run(7)
        assertTrue(first[0].isNotEmpty())
        for (k in first.indices) assertContentEquals(first[k], second[k])
        assertTrue(!run(8)[0].contentEquals(first[0]), "Another seed gives another run.")
    }

    @Test
    fun parentedEmitterEmitsAtItsWorldPosition() {
        val world = World()
        val parent = world.create()
        world.add(parent, Transform, Transform(x = 100f, y = 50f, scaleX = 2f, scaleY = 2f))
        val emitter = ParticleEmitter(region)
        val child = world.emitterAt(10f, 5f, emitter)
        world.setParent(child, parent)
        world.propagateTransforms()

        emitter.emit(1)
        world.advanceParticles(0f)
        assertEquals(120f, emitter.xs[0])
        assertEquals(60f, emitter.ys[0])
    }

    @Test
    fun emitterWithoutTransformIsSkipped() {
        val world = World()
        val emitter = ParticleEmitter(region).apply { rate = 100f }
        val entity = world.create()
        world.add(entity, ParticleEmitter, emitter)
        emitter.emit(5)

        assertEquals(0, world.advanceParticles(1f))
        assertEquals(0, emitter.count)
    }

    @Test
    fun rejectsInvalidSettings() {
        assertFailsWith<IllegalArgumentException> { ParticleEmitter(region, maxParticles = 0) }
        val emitter = ParticleEmitter(region)
        assertFailsWith<IllegalArgumentException> { emitter.emit(-1) }
        assertFailsWith<IllegalArgumentException> { emitter.rate = -1f }
        assertFailsWith<IllegalArgumentException> { emitter.rate = Float.POSITIVE_INFINITY }
        assertFailsWith<IllegalArgumentException> { emitter.spread = 361f }
        assertFailsWith<IllegalArgumentException> { emitter.lifetime = -1f..1f }
        assertFailsWith<IllegalArgumentException> { emitter.speed = 2f..1f }
        assertFailsWith<IllegalArgumentException> { emitter.angularVelocity = 0f..Float.NaN }
        assertFailsWith<IllegalArgumentException> { World().advanceParticles(-1f) }
    }

    @Test
    fun toStringShowsCountAndSettings() {
        val emitter = ParticleEmitter(region, maxParticles = 16).apply { rate = 30f }
        assertEquals(
            "ParticleEmitter(count=0/16, rate=30.0, layer=0, visible=true, region=TextureRegion(x=0, y=0, size=8x8, texture=Texture(8x8)))",
            emitter.toString(),
        )
    }
}
