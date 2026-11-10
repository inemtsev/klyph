package klyph.scene

import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.Overlap
import klyph.collision.Shape
import klyph.entity.Entity
import klyph.entity.Lifetime
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.entity.World
import klyph.entity.advanceLifetimes
import klyph.entity.applyVelocities
import klyph.event.Events
import klyph.particle.EmitterEnd
import klyph.particle.ParticleEmitter
import klyph.particle.advanceParticles
import klyph.platform.Color
import klyph.render.Fade
import klyph.render.FadeEnd
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import klyph.render.SpriteSheet
import klyph.render.Texture
import klyph.render.advanceFades
import klyph.render.advanceSpriteAnimations
import klyph.render.asRegion
import klyph.render.fromGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FrozenSceneTest {
    private val sheet = SpriteSheet.fromGrid(Texture(32, 16), columns = 2, rows = 1)

    private fun World.emitterAt(x: Float, emitter: ParticleEmitter): Entity {
        val entity = create()
        add(entity, Transform, Transform(x = x, y = 0f))
        add(entity, ParticleEmitter, emitter)
        return entity
    }

    private fun World.animated(sprite: Sprite): Entity {
        val entity = create()
        add(entity, Sprite, sprite)
        add(entity, SpriteAnimation, SpriteAnimation(sheet, 100.milliseconds))
        return entity
    }

    private fun World.colliderAt(x: Float): Entity {
        val entity = create()
        add(entity, Transform, Transform(x = x, y = 0f))
        add(entity, Collider, Collider(Shape.Circle(10f)))
        return entity
    }

    /** Pushes `play`, which runs [playEnter], then `pause` over it, which runs [pauseEnter]. */
    private fun SceneHarness.playUnderPause(playEnter: () -> Unit, pauseEnter: () -> Unit) {
        stack.push(scene("play", enter = playEnter))
        stack.push(scene("pause", enter = pauseEnter))
        applyTransitions()
    }

    @Test
    fun emitterOfAFrozenSceneNeitherAdvancesNorEmits() {
        val h = SceneHarness()
        val region = Texture(8, 8).asRegion()
        val frozen = ParticleEmitter(region).apply { rate = 100f }
        val running = ParticleEmitter(region).apply { rate = 100f }
        val unowned = ParticleEmitter(region).apply { rate = 100f }
        h.world.emitterAt(0f, unowned)
        h.playUnderPause(
            playEnter = { h.world.emitterAt(0f, frozen) },
            pauseEnter = { h.world.emitterAt(0f, running) },
        )
        frozen.emit(3)

        assertEquals(10 + 10, h.world.advanceParticles(0.1f, h.stack))
        assertEquals(0, frozen.particleCount, "A frozen emitter does not emit, not even a requested burst.")
        assertEquals(10, running.particleCount)
        assertEquals(10, unowned.particleCount)

        h.stack.pop()
        h.applyTransitions()
        h.world.advanceParticles(0.1f, h.stack)
        assertEquals(13, frozen.particleCount, "Once the scene above is gone the emitter runs again.")
    }

    @Test
    fun particlesOfAFrozenEmitterStandStillAndStillCount() {
        val h = SceneHarness()
        val frozen = ParticleEmitter(Texture(8, 8).asRegion()).apply { lifetime = 1.seconds..1.seconds }
        h.stack.push(h.scene("play", enter = { h.world.emitterAt(5f, frozen) }))
        h.applyTransitions()
        frozen.emit(4)
        h.world.advanceParticles(0f, h.stack)
        val age = frozen.ages[0]

        h.stack.push(h.scene("pause"))
        h.applyTransitions()
        assertEquals(4, h.world.advanceParticles(0.5f, h.stack))
        assertEquals(age, frozen.ages[0])
    }

    @Test
    fun animationOfAFrozenSceneDoesNotAdvance() {
        val h = SceneHarness()
        val frozen = Sprite(sheet[0])
        val running = Sprite(sheet[0])
        h.playUnderPause(
            playEnter = { h.world.animated(frozen) },
            pauseEnter = { h.world.animated(running) },
        )
        h.world.advanceSpriteAnimations(100.milliseconds, h.stack)
        assertSame(sheet[0], frozen.region)
        assertSame(sheet[1], running.region)
    }

    @Test
    fun colliderOfAFrozenSceneProducesNoPairAndNoQueryHit() {
        val h = SceneHarness()
        var top = NO_ENTITY
        h.playUnderPause(
            playEnter = { h.world.colliderAt(0f) },
            pauseEnter = { top = h.world.colliderAt(5f) },
        )
        val unowned = h.world.colliderAt(-5f)
        val collisions = Collisions()
        val events = Events()

        collisions.detect(h.world, events, h.stack)
        val pairs = ArrayList<Overlap>()
        events.forEach<Overlap> { pairs += it }
        assertEquals(1, pairs.size, "Only the top scene's collider and the unowned one overlap.")
        assertEquals(1, collisions.pairCount)
        assertEquals(listOf(top, unowned), collisions.overlappingTo(ArrayList(), Shape.Circle(1f), x = 0f, y = 0f))

        // Without the stack, as on an instance a user drives, nothing is left out.
        collisions.detect(h.world, Events())
        assertEquals(3, collisions.pairCount)
    }

    @Test
    fun lifetimeOfAFrozenSceneDoesNotCountDown() {
        val h = SceneHarness()
        val frozen = Lifetime(1.seconds)
        val running = Lifetime(1.seconds)
        var top = NO_ENTITY
        h.playUnderPause(
            playEnter = { h.world.add(h.world.create(), Lifetime, frozen) },
            pauseEnter = { top = h.world.create().also { h.world.add(it, Lifetime, running) } },
        )
        h.world.advanceLifetimes(1.seconds, h.commands, h.stack)
        assertEquals(1.seconds, frozen.remaining)
        assertEquals(0.seconds, running.remaining)
        assertEquals(1, h.commands.size, "Only the top scene's entity is queued.")
        h.commands.apply()
        assertTrue(!h.world.isAlive(top))
    }

    @Test
    fun velocityOfAFrozenSceneDoesNotMove() {
        val h = SceneHarness()
        val frozen = Transform()
        val running = Transform()
        val unowned = Transform()
        fun World.moving(transform: Transform) = create().also {
            add(it, Transform, transform)
            add(it, Velocity, Velocity(dx = 10f, dy = 20f))
        }
        h.world.moving(unowned)
        h.playUnderPause(playEnter = { h.world.moving(frozen) }, pauseEnter = { h.world.moving(running) })
        h.world.applyVelocities(1f, h.stack)
        assertEquals(0f, frozen.x)
        assertEquals(10f, running.x)
        assertEquals(20f, unowned.y)
    }

    @Test
    fun fadeOfAFrozenSceneDoesNotAdvance() {
        val h = SceneHarness()
        val region = Texture(8, 8).asRegion()
        val frozen = Sprite(region)
        val running = Sprite(region)
        h.playUnderPause(
            playEnter = { h.world.create().also { h.world.add(it, Sprite, frozen); h.world.add(it, Fade, Fade(1.seconds, end = FadeEnd.DESTROY)) } },
            pauseEnter = { h.world.create().also { h.world.add(it, Sprite, running); h.world.add(it, Fade, Fade(1.seconds)) } },
        )
        h.world.advanceFades(500.milliseconds, h.commands, h.stack)
        assertEquals(Color.WHITE, frozen.color)
        assertEquals(0.5f, running.color.alpha, 1f / 255f)
        h.world.advanceFades(1.seconds, h.commands, h.stack)
        assertTrue(h.commands.isEmpty(), "The frozen DESTROY fade never finishes while its scene is frozen.")
    }

    @Test
    fun emptyEmitterOfAFrozenSceneIsNotDestroyed() {
        val h = SceneHarness()
        val frozen = ParticleEmitter(Texture(8, 8).asRegion()).apply { whenEmpty = EmitterEnd.DESTROY }
        h.playUnderPause(playEnter = { h.world.emitterAt(0f, frozen) }, pauseEnter = {})
        h.world.advanceParticles(1f, h.stack, h.commands)
        assertTrue(h.commands.isEmpty())
    }
}
