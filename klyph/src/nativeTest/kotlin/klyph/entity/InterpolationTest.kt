package klyph.entity

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterpolationTest {
    @Test
    fun recordCopiesTransformsOfMarkedEntitiesOnly() {
        val world = World()
        val marked = world.create()
        world.add(marked, Transform, Transform(x = 1f, y = 2f, rotation = 0.5f, scaleX = 2f, scaleY = 3f))
        world.add(marked, Interpolated, Interpolated)
        val plain = world.create()
        world.add(plain, Transform, Transform(x = 5f))
        val markerOnly = world.create()
        world.add(markerOnly, Interpolated, Interpolated)

        world.recordPreviousTransforms()

        assertEquals("PreviousTransform(x=1.0, y=2.0, rotation=0.5, scale=2.0 x 3.0)", world.get(marked, PreviousTransform).toString())
        assertNull(world.getOrNull(plain, PreviousTransform))
        assertNull(world.getOrNull(markerOnly, PreviousTransform))
    }

    @Test
    fun recordUpdatesTheExistingCopy() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 1f)
        world.add(entity, Transform, transform)
        world.add(entity, Interpolated, Interpolated)
        world.recordPreviousTransforms()
        val copy = world.get(entity, PreviousTransform)

        transform.x = 4f
        transform.rotation = 1f
        world.recordPreviousTransforms()

        assertTrue(copy === world.get(entity, PreviousTransform))
        assertEquals(4f, copy.x)
        assertEquals(1f, copy.rotation)
        assertEquals(1, world.count(PreviousTransform))
    }

    @Test
    fun markerAddedLaterGetsItsCopyOnTheNextRecord() {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform(x = 3f))
        world.recordPreviousTransforms()
        assertNull(world.getOrNull(entity, PreviousTransform))

        world.add(entity, Interpolated, Interpolated)
        assertNull(world.getOrNull(entity, PreviousTransform))
        world.recordPreviousTransforms()
        assertEquals(3f, world.get(entity, PreviousTransform).x)
    }

    @Test
    fun recordDropsCopiesOfEntitiesThatLostTheMarkerOrTheTransform() {
        val world = World()
        val unmarked = world.create()
        val untransformed = world.create()
        val kept = world.create()
        for (entity in listOf(unmarked, untransformed, kept)) {
            world.add(entity, Transform, Transform())
            world.add(entity, Interpolated, Interpolated)
        }
        world.recordPreviousTransforms()
        assertEquals(3, world.count(PreviousTransform))

        world.remove(unmarked, Interpolated)
        world.remove(untransformed, Transform)
        world.recordPreviousTransforms()

        assertNull(world.getOrNull(unmarked, PreviousTransform))
        assertNull(world.getOrNull(untransformed, PreviousTransform))
        assertTrue(world.has(kept, PreviousTransform))

        world.remove(kept, Interpolated)
        world.recordPreviousTransforms()
        assertEquals(0, world.count(PreviousTransform))
    }

    @Test
    fun blendRotationInterpolatesInsideOneTurn() {
        assertEquals(0.5f, blendRotation(0f, 1f, 0.5f))
        assertEquals(1f, blendRotation(1f, 2f, 0f))
        assertEquals(2f, blendRotation(1f, 2f, 1f), 1e-6f)
    }

    @Test
    fun blendRotationTakesTheShortArcAcrossTheSeam() {
        val halfway = blendRotation(3f, -3f, 0.5f)
        // 3 and -3 are 0.28 apart through pi, and 6 apart through 0.
        assertTrue(abs(abs(halfway) - PI.toFloat()) < 1e-3f, "Halfway rotation $halfway is not near pi.")
        val back = blendRotation(-3f, 3f, 0.5f)
        assertTrue(abs(abs(back) - PI.toFloat()) < 1e-3f, "Halfway rotation $back is not near pi.")
    }

    @Test
    fun blendRotationIgnoresWholeTurns() {
        val twoPi = (2 * PI).toFloat()
        assertEquals(0.25f, blendRotation(0f, 0.5f + 3 * twoPi, 0.5f), 1e-4f)
        assertEquals(1f, blendRotation(1f, 1f + twoPi, 0.5f), 1e-4f)
    }
}
