package klyph.entity

import kotlin.test.Test
import kotlin.test.assertEquals

class VelocityTest {
    @Test
    fun movesTheTransformByVelocityTimesTheStep() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 10f, y = 20f)
        world.add(entity, Transform, transform)
        world.add(entity, Velocity, Velocity(dx = 60f, dy = -120f))

        world.applyVelocities(0.5f)
        assertEquals(40f, transform.x)
        assertEquals(-40f, transform.y)
    }

    @Test
    fun movesLikeAFixedUpdateSystemStepAfterStep() {
        val world = World()
        val byEngine = world.create()
        val byHand = Transform(x = 3f, y = 7f)
        val moved = Transform(x = 3f, y = 7f)
        world.add(byEngine, Transform, moved)
        world.add(byEngine, Velocity, Velocity(dx = 37.5f, dy = 91f))
        val step = 1f / 60f
        repeat(120) {
            world.applyVelocities(step)
            byHand.x += 37.5f * step
            byHand.y += 91f * step
        }
        assertEquals(byHand.x, moved.x)
        assertEquals(byHand.y, moved.y)
    }

    @Test
    fun anEntityWithoutATransformIsLeftAlone() {
        val world = World()
        val entity = world.create()
        world.add(entity, Velocity, Velocity(dx = 1f))
        world.applyVelocities(1f)
        assertEquals("Velocity(dx=1.0, dy=0.0)", world.get(entity, Velocity).toString())
    }

    @Test
    fun interpolatedEntitiesRecordTheirTransformBeforeTheMove() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 0f)
        world.add(entity, Transform, transform)
        world.add(entity, Velocity, Velocity(dx = 60f))
        world.add(entity, Interpolated, Interpolated)
        // The loop's order within a fixed step: record, FIXED_UPDATE systems, velocities.
        world.recordPreviousTransforms()
        world.applyVelocities(1f / 60f)
        world.recordPreviousTransforms()
        world.applyVelocities(1f / 60f)
        assertEquals(1f, world.get(entity, PreviousTransform).x)
        assertEquals(2f, transform.x)
    }
}
