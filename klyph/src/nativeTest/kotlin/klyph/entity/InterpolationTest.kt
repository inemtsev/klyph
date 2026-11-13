package klyph.entity

import klyph.math.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

        assertEquals("PreviousTransform(x=1.0, y=2.0, rotation=0.5, scale=2.0x3.0, firstStep=true)", world.get(marked, PreviousTransform).toString())
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
    fun onlyTheFirstRecordIsMarkedAsTheFirstStep() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 0f)
        world.add(entity, Transform, transform)
        world.add(entity, Interpolated, Interpolated)

        // Step 1 records the spawn point, then a system places the entity on its path.
        world.recordPreviousTransforms()
        transform.x = 100f
        assertTrue(world.get(entity, PreviousTransform).isFirstStep, "The spawn point is no step to blend from.")

        // Step 2 records where step 1 left the entity: from now on it blends.
        world.recordPreviousTransforms()
        transform.x = 110f
        val previous = world.get(entity, PreviousTransform)
        assertFalse(previous.isFirstStep)
        assertEquals(100f, previous.x)
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

    @Test
    fun drawnPositionIsTheTransformUntilTheSecondStep() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 0f, y = 0f)
        world.add(entity, Transform, transform)
        world.add(entity, Interpolated, Interpolated)

        transform.x = 5f
        assertEquals(Vec2(5f, 0f), world.drawnPositionOf(entity, 0.5f), "Nothing is recorded before the first step.")

        // The first record is the spawn point, which is no step to blend from.
        world.recordPreviousTransforms()
        transform.x = 100f
        transform.y = 20f
        assertEquals(Vec2(100f, 20f), world.drawnPositionOf(entity, 0.5f))
    }

    @Test
    fun drawnPositionBlendsBetweenTheLastTwoStepsFromTheSecondStepOn() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 0f, y = 0f)
        world.add(entity, Transform, transform)
        world.add(entity, Interpolated, Interpolated)
        world.recordPreviousTransforms()
        transform.x = 100f
        transform.y = 20f
        world.recordPreviousTransforms()
        transform.x = 110f
        transform.y = 30f

        assertEquals(Vec2(100f, 20f), world.drawnPositionOf(entity, 0f))
        assertEquals(Vec2(102.5f, 22.5f), world.drawnPositionOf(entity, 0.25f))
        assertEquals(Vec2(105f, 25f), world.drawnPositionOf(entity, 0.5f))
        assertEquals(Vec2(110f, 30f), world.drawnPositionOf(entity, 1f))
    }

    @Test
    fun drawnPositionOfAnEntityWithoutTheMarkerIsItsTransform() {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform(x = 7f, y = 8f))
        world.recordPreviousTransforms()
        world.recordPreviousTransforms()

        assertEquals(Vec2(7f, 8f), world.drawnPositionOf(entity, 0.5f))
    }

    @Test
    fun drawnPositionOfAChildIsItsWorldPositionAndNotBlended() {
        val world = World()
        val parent = world.create()
        val parentTransform = Transform(x = 100f, y = 50f)
        world.add(parent, Transform, parentTransform)
        world.add(parent, Interpolated, Interpolated)
        val child = world.create()
        world.add(child, Transform, Transform(x = 20f))
        world.add(child, Interpolated, Interpolated)
        world.setParent(child, parent)
        world.recordPreviousTransforms()
        world.recordPreviousTransforms()
        parentTransform.x = 110f
        world.propagateTransforms()

        // The child is at its world transform, computed from the parent's current Transform.
        assertEquals(Vec2(130f, 50f), world.drawnPositionOf(child, 0.5f))
        assertEquals(Vec2(105f, 50f), world.drawnPositionOf(parent, 0.5f), "The root is blended.")
    }

    @Test
    fun drawnPositionTakesAnAlphaFromZeroToOne() {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform())

        world.drawnPositionOf(entity, 0f)
        world.drawnPositionOf(entity, 1f)
        for (alpha in listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException>("Alpha $alpha") { world.drawnPositionOf(entity, alpha) }
        }
    }

    @Test
    fun drawnPositionNeedsALiveEntityWithATransform() {
        val world = World()
        val bare = world.create()
        val gone = world.create()
        world.add(gone, Transform, Transform())
        world.destroy(gone)

        assertFailsWith<NoSuchElementException> { world.drawnPositionOf(bare, 0.5f) }
        assertFailsWith<NoSuchElementException> { world.drawnPositionOf(gone, 0.5f) }
    }

    @Test
    fun drawnTransformPicksTheHierarchyThenTheBlendThenTheTransform() {
        val local = Transform(x = 10f, y = 20f, rotation = 1f, scaleX = 2f, scaleY = 4f)
        val previous = PreviousTransform(x = 0f, y = 0f, rotation = 0f, scaleX = 1f, scaleY = 2f)
        val hierarchy = WorldTransform(x = 7f, y = 8f, rotation = 0.5f, scaleX = 3f, scaleY = 3f)
        fun drawn(hierarchy: WorldTransform?, previous: PreviousTransform?) =
            drawnTransform(local, hierarchy, previous, 0.5f) { x, y, rotation, scaleX, scaleY -> listOf(x, y, rotation, scaleX, scaleY) }

        assertEquals(listOf(7f, 8f, 0.5f, 3f, 3f), drawn(hierarchy, previous), "A world transform wins over a record.")
        assertEquals(listOf(5f, 10f, 0.5f, 1.5f, 3f), drawn(null, previous), "Every field is blended halfway.")
        previous.isFirstStep = true
        assertEquals(listOf(10f, 20f, 1f, 2f, 4f), drawn(null, previous), "A first step is no step to blend from.")
        assertEquals(listOf(10f, 20f, 1f, 2f, 4f), drawn(null, null))
    }

    @Test
    fun snapInterpolationCopiesTheWholeTransformAndClearsTheFirstStep() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 1f, y = 2f, rotation = 0.5f, scaleX = 2f, scaleY = 3f)
        world.add(entity, Transform, transform)
        world.add(entity, Interpolated, Interpolated)
        world.recordPreviousTransforms()
        val previous = world.get(entity, PreviousTransform)
        assertTrue(previous.isFirstStep)

        transform.x = 400f
        transform.y = 500f
        transform.rotation = 2f
        transform.scaleX = 5f
        transform.scaleY = 6f
        world.snapInterpolation(entity)

        assertEquals("PreviousTransform(x=400.0, y=500.0, rotation=2.0, scale=5.0x6.0, firstStep=false)", previous.toString())
        assertTrue(previous === world.get(entity, PreviousTransform), "The record is changed, not replaced.")
    }

    @Test
    fun snapInterpolationDrawsAJumpWhereItLandedAtEveryAlpha() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 0f, y = 0f)
        world.add(entity, Transform, transform)
        world.add(entity, Interpolated, Interpolated)
        world.recordPreviousTransforms()
        transform.x = 10f
        world.recordPreviousTransforms()
        transform.x = 20f

        // A respawn in the step: without a snap the picture sweeps from the last step's place.
        transform.x = 500f
        assertEquals(Vec2(255f, 0f), world.drawnPositionOf(entity, 0.5f))

        world.snapInterpolation(entity)
        for (alpha in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(Vec2(500f, 0f), world.drawnPositionOf(entity, alpha), "Alpha $alpha")
        }

        // The next step records the new place, so the entity blends from there as before.
        world.recordPreviousTransforms()
        transform.x = 510f
        assertEquals(Vec2(505f, 0f), world.drawnPositionOf(entity, 0.5f))
    }

    @Test
    fun snapInterpolationDoesNothingWithoutARecordOrATransform() {
        val world = World()
        val plain = world.create()
        world.add(plain, Transform, Transform(x = 3f))
        val gone = world.create()
        world.add(gone, Transform, Transform())
        world.destroy(gone)
        val untransformed = world.create()
        world.add(untransformed, Transform, Transform(x = 9f))
        world.add(untransformed, Interpolated, Interpolated)
        world.recordPreviousTransforms()
        world.remove(untransformed, Transform)

        world.snapInterpolation(plain)
        world.snapInterpolation(gone)
        world.snapInterpolation(untransformed)

        assertNull(world.getOrNull(plain, PreviousTransform))
        assertEquals(9f, world.get(untransformed, PreviousTransform).x)
        assertTrue(world.get(untransformed, PreviousTransform).isFirstStep, "A record without a Transform to copy is left alone.")

        val fresh = World()
        val entity = fresh.create()
        fresh.add(entity, Transform, Transform(x = 4f))
        fresh.add(entity, Interpolated, Interpolated)
        fresh.snapInterpolation(entity)
        assertNull(fresh.getOrNull(entity, PreviousTransform), "Snapping before the first step creates no record.")
        assertEquals(Vec2(4f, 0f), fresh.drawnPositionOf(entity, 0.5f))
    }
}
