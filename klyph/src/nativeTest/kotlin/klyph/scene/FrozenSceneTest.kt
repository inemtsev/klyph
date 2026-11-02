package klyph.scene

import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.Overlap
import klyph.collision.Shape
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.event.Events
import klyph.particle.ParticleEmitter
import klyph.particle.advanceParticles
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import klyph.render.SpriteSheet
import klyph.render.Texture
import klyph.render.advanceSpriteAnimations
import klyph.render.asRegion
import klyph.render.fromGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

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
        assertEquals(0, frozen.count, "A frozen emitter does not emit, not even a requested burst.")
        assertEquals(10, running.count)
        assertEquals(10, unowned.count)

        h.stack.pop()
        h.applyTransitions()
        h.world.advanceParticles(0.1f, h.stack)
        assertEquals(13, frozen.count, "Once the scene above is gone the emitter runs again.")
    }

    @Test
    fun particlesOfAFrozenEmitterStandStillAndStillCount() {
        val h = SceneHarness()
        val frozen = ParticleEmitter(Texture(8, 8).asRegion()).apply { lifetime = 1f..1f }
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
        assertEquals(listOf(top, unowned), collisions.overlapsTo(ArrayList(), Shape.Circle(1f), x = 0f, y = 0f))

        // Without the stack, as on an instance a user drives, nothing is left out.
        collisions.detect(h.world, Events())
        assertEquals(3, collisions.pairCount)
    }
}
