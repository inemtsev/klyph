package klyph.collision

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.event.Events
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SweepTest {
    private fun World.solid(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        passable: Passable = Passable.NEVER,
        layers: CollisionLayers = CollisionLayers.DEFAULT,
    ): Entity {
        val entity = create()
        add(entity, Transform, Transform(x = (left + right) / 2, y = (top + bottom) / 2))
        add(entity, Collider, Collider(Shape.Box(width = right - left, height = bottom - top), layers = layers))
        add(entity, Solid, Solid(passable))
        return entity
    }

    /** A collisions instance that answers sweeps for [world]. */
    private fun collisionsFor(world: World, cellSize: Float = 64f): Collisions =
        Collisions(cellSize).also { it.detect(world, Events()) }

    @Test
    fun returnsTheFractionAndNormalOfTheFaceHit() {
        val world = World()
        val wall = world.solid(left = 50f, top = -100f, right = 60f, bottom = 100f)
        val collisions = collisionsFor(world)
        // The box's right edge starts at 5 and reaches the wall's left face at 50 after 45 of 100.
        val hit = assertNotNull(collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 100f, dy = 0f))
        assertEquals(SweepHit(wall, 0.45f, -1f, 0f), hit)
        assertEquals("SweepHit(entity=$wall, fraction=0.45, normalX=-1.0, normalY=0.0)", hit.toString())

        val back = assertNotNull(collisions.sweepBox(x = 100f, y = 0f, width = 10f, height = 10f, dx = -100f, dy = 0f))
        assertEquals(0.35f, back.fraction, 0.00001f)
        assertEquals(1f, back.normalX)

        val floor = world.solid(left = -100f, top = 40f, right = 40f, bottom = 50f)
        val down = assertNotNull(collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 70f))
        assertEquals(SweepHit(floor, 0.5f, 0f, -1f), down)
        val up = assertNotNull(collisions.sweepBox(x = 0f, y = 80f, width = 10f, height = 10f, dx = 0f, dy = -70f))
        assertEquals(SweepHit(floor, 25f / 70f, 0f, 1f), up)
    }

    @Test
    fun aMoveThatFallsShortOrPassesBesideHitsNothing() {
        val world = World()
        world.solid(left = 50f, top = -100f, right = 60f, bottom = 100f)
        val collisions = collisionsFor(world)
        assertNull(collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 44f, dy = 0f))
        assertNull(collisions.sweepBox(x = 0f, y = 200f, width = 10f, height = 10f, dx = 100f, dy = 0f))
        // Sliding along the face it touches is no hit.
        assertNull(collisions.sweepBox(x = 45f, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 50f))
        // Nor is moving away from it.
        assertNull(collisions.sweepBox(x = 45f, y = 0f, width = 10f, height = 10f, dx = -50f, dy = 0f))
    }

    @Test
    fun theNearestOfSeveralSolidsIsReturnedAndAllAreListedInOrder() {
        val world = World()
        val far = world.solid(left = 80f, top = -10f, right = 90f, bottom = 10f)
        val near = world.solid(left = 30f, top = -10f, right = 40f, bottom = 10f)
        world.solid(left = 30f, top = 100f, right = 40f, bottom = 120f)
        val collisions = collisionsFor(world)
        val hit = assertNotNull(collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 100f, dy = 0f))
        assertEquals(near, hit.entity)
        val all = collisions.sweepBoxTo(ArrayList(), x = 0f, y = 0f, width = 10f, height = 10f, dx = 100f, dy = 0f)
        assertEquals(listOf(near, far), all.map { it.entity })
        assertEquals(listOf(0.25f, 0.75f), all.map { it.fraction })
    }

    @Test
    fun aSolidSpanningManyCellsIsFoundOnceFromEverySide() {
        val world = World()
        val slab = world.solid(left = 0f, top = 0f, right = 500f, bottom = 300f)
        val collisions = collisionsFor(world, cellSize = 16f)
        val fromAbove = assertNotNull(collisions.sweepBox(x = 250f, y = -100f, width = 20f, height = 20f, dx = 0f, dy = 200f))
        assertEquals(SweepHit(slab, 90f / 200f, 0f, -1f), fromAbove)
        val fromLeft = assertNotNull(collisions.sweepBox(x = -50f, y = 150f, width = 20f, height = 20f, dx = 100f, dy = 0f))
        assertEquals(SweepHit(slab, 0.4f, -1f, 0f), fromLeft)
        val fromBelow = assertNotNull(collisions.sweepBox(x = 400f, y = 350f, width = 20f, height = 20f, dx = 0f, dy = -100f))
        assertEquals(SweepHit(slab, 0.4f, 0f, 1f), fromBelow)
        val diagonal = collisions.sweepBoxTo(ArrayList(), x = 600f, y = 400f, width = 20f, height = 20f, dx = -400f, dy = -400f)
        assertEquals(listOf(slab), diagonal.map { it.entity })
    }

    @Test
    fun aSolidTooLargeForTheGridIsStillFound() {
        val world = World()
        val ground = world.solid(left = -100_000f, top = 0f, right = 100_000f, bottom = 1000f)
        val collisions = collisionsFor(world, cellSize = 8f)
        val hit = assertNotNull(collisions.sweepBox(x = 5000f, y = -20f, width = 10f, height = 10f, dx = 0f, dy = 30f))
        assertEquals(SweepHit(ground, 0.5f, 0f, -1f), hit)
    }

    @Test
    fun aZeroLengthSweepReportsWhatOverlapsHere() {
        val world = World()
        val block = world.solid(left = 0f, top = 0f, right = 100f, bottom = 100f)
        world.solid(left = 0f, top = 200f, right = 100f, bottom = 210f, passable = Passable.FROM_BELOW)
        val collisions = collisionsFor(world)
        assertEquals(SweepHit(block, 0f, 0f, 0f), collisions.sweepBox(x = 50f, y = 50f, width = 10f, height = 10f, dx = 0f, dy = 0f))
        assertNull(collisions.sweepBox(x = 200f, y = 50f, width = 10f, height = 10f, dx = 0f, dy = 0f))
        // Touching along an edge is not overlapping.
        assertNull(collisions.sweepBox(x = 105f, y = 50f, width = 10f, height = 10f, dx = 0f, dy = 0f))
        // A one-way solid stops only a box moving down onto it.
        assertNull(collisions.sweepBox(x = 50f, y = 205f, width = 10f, height = 10f, dx = 0f, dy = 0f))
    }

    @Test
    fun aSweepStartingInsideASolidHitsItAtTheStart() {
        val world = World()
        val block = world.solid(left = 0f, top = 0f, right = 100f, bottom = 100f)
        val collisions = collisionsFor(world)
        assertEquals(SweepHit(block, 0f, 0f, 0f), collisions.sweepBox(x = 95f, y = 50f, width = 20f, height = 20f, dx = 100f, dy = 0f))
    }

    @Test
    fun aOneWaySolidStopsOnlyABoxComingDownFromAbove() {
        val world = World()
        val ledge = world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f, passable = Passable.FROM_BELOW)
        val collisions = collisionsFor(world)
        assertEquals(SweepHit(ledge, 0.5f, 0f, -1f), collisions.sweepBox(x = 100f, y = 80f, width = 20f, height = 20f, dx = 0f, dy = 20f))
        // The bottom edge exactly at the top still lands.
        assertEquals(SweepHit(ledge, 0f, 0f, -1f), collisions.sweepBox(x = 100f, y = 90f, width = 20f, height = 20f, dx = 0f, dy = 20f))
        assertNull(collisions.sweepBox(x = 100f, y = 150f, width = 20f, height = 20f, dx = 0f, dy = -100f))
        assertNull(collisions.sweepBox(x = 100f, y = 92f, width = 20f, height = 20f, dx = 0f, dy = 20f))
        assertNull(collisions.sweepBox(x = -50f, y = 105f, width = 20f, height = 20f, dx = 100f, dy = 0f))
    }

    @Test
    fun theMaskLeavesOutSolidsOnOtherLayers() {
        val world = World()
        val water = CollisionLayers.of(4)
        val pool = world.solid(left = 0f, top = 40f, right = 100f, bottom = 50f, layers = water)
        val stone = world.solid(left = 0f, top = 80f, right = 100f, bottom = 90f)
        val collisions = collisionsFor(world)
        assertEquals(stone, collisions.sweepBox(x = 50f, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 100f, mask = CollisionLayers.DEFAULT)?.entity)
        assertEquals(pool, collisions.sweepBox(x = 50f, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 100f)?.entity)
        assertNull(collisions.sweepBox(x = 50f, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 100f, mask = CollisionLayers.NONE))
    }

    @Test
    fun aSolidAddedOrRemovedIsSeenByTheNextSweep() {
        val world = World()
        val collisions = Collisions()
        collisions.detect(world, Events())
        assertNull(collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 100f, dy = 0f))
        val wall = world.solid(left = 50f, top = -10f, right = 60f, bottom = 10f)
        assertEquals(wall, collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 100f, dy = 0f)?.entity)
        assertEquals(1, collisions.solidCount)
        world.destroy(wall)
        assertNull(collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 100f, dy = 0f))
        assertEquals(0, collisions.solidCount)
    }

    @Test
    fun aSolidMovedByItsTransformIsFoundWhereItIsAfterTheNextRefresh() {
        val world = World()
        val wall = world.solid(left = 50f, top = -10f, right = 60f, bottom = 10f)
        val collisions = collisionsFor(world)
        world.get(wall, Transform).x = 155f
        // Until the next detection or body move the grid still holds the wall where it was.
        assertEquals(0.45f, collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 100f, dy = 0f)?.fraction)
        collisions.detect(world, Events())
        assertNull(collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 100f, dy = 0f))
        assertEquals(0.725f, collisions.sweepBox(x = 0f, y = 0f, width = 10f, height = 10f, dx = 200f, dy = 0f)?.fraction)
    }

    @Test
    fun solidsAreAlsoOrdinaryColliders() {
        val world = World()
        world.solid(left = 0f, top = 0f, right = 100f, bottom = 100f)
        val coin = world.create()
        world.add(coin, Transform, Transform(x = 50f, y = 50f))
        world.add(coin, Collider, Collider(Shape.Circle(4f)))
        val collisions = Collisions()
        collisions.detect(world, Events())
        assertEquals(1, collisions.pairCount)
        assertEquals(listOf(Entity.of(0, 0)), collisions.overlapping(Shape.Circle(1f), x = 10f, y = 10f))
    }

    @Test
    fun theGridAnswersAsTestingEverySolidWould() {
        val random = Random(7)
        val world = World()
        repeat(300) {
            val x = random.nextFloat() * 2000f - 1000f
            val y = random.nextFloat() * 2000f - 1000f
            val width = 4f + random.nextFloat() * 120f
            val height = 4f + random.nextFloat() * 120f
            val passable = if (random.nextInt(4) == 0) Passable.FROM_BELOW else Passable.NEVER
            world.solid(left = x, top = y, right = x + width, bottom = y + height, passable = passable)
        }
        val grid = collisionsFor(world, cellSize = 32f)
        // One cell holds every solid, so every sweep tests them all.
        val flat = collisionsFor(world, cellSize = 1_000_000f)
        repeat(2000) {
            val x = random.nextFloat() * 2400f - 1200f
            val y = random.nextFloat() * 2400f - 1200f
            val width = 1f + random.nextFloat() * 60f
            val height = 1f + random.nextFloat() * 60f
            val dx = (random.nextFloat() - 0.5f) * 1200f
            val dy = (random.nextFloat() - 0.5f) * 1200f
            val expected = flat.sweepBox(x = x, y = y, width = width, height = height, dx = dx, dy = dy)
            val actual = grid.sweepBox(x = x, y = y, width = width, height = height, dx = dx, dy = dy)
            assertEquals(expected, actual, "Sweep $it from ($x, $y) by ($dx, $dy).")
            val expectedAll = flat.sweepBoxTo(ArrayList(), x = x, y = y, width = width, height = height, dx = dx, dy = dy)
            val actualAll = grid.sweepBoxTo(ArrayList(), x = x, y = y, width = width, height = height, dx = dx, dy = dy)
            assertEquals(expectedAll, actualAll, "Sweep $it from ($x, $y) by ($dx, $dy).")
            if (expectedAll.isNotEmpty()) assertEquals(expectedAll.first(), expected)
        }
    }

    @Test
    fun invalidArgumentsAreRejected() {
        val collisions = Collisions()
        val start = assertFailsWith<IllegalArgumentException> {
            collisions.sweepBox(x = Float.NaN, y = 0f, width = 1f, height = 1f, dx = 0f, dy = 0f)
        }
        assertEquals("Sweep start (NaN, 0.0) is not finite.", start.message)
        val size = assertFailsWith<IllegalArgumentException> {
            collisions.sweepBox(x = 0f, y = 0f, width = 0f, height = 1f, dx = 0f, dy = 0f)
        }
        assertEquals("Sweep box size 0.0x1.0 is not a positive finite size.", size.message)
        assertFailsWith<IllegalArgumentException> {
            collisions.sweepBoxTo(ArrayList(), x = 0f, y = 0f, width = 1f, height = 1f, dx = Float.POSITIVE_INFINITY, dy = 0f)
        }
        assertTrue(collisions.sweepBoxTo(ArrayList(), x = 0f, y = 0f, width = 1f, height = 1f, dx = 5f, dy = 0f).isEmpty())
    }
}
