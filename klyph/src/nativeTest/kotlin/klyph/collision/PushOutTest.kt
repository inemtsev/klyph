package klyph.collision

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A body that starts a move overlapping a solid is pushed out of it, or left in place and reported stuck. */
class PushOutTest {
    private val step = 1f / 60f
    private val gap = Collisions.CONTACT_GAP

    /** Spawns a box solid from ([left], [top]) to ([right], [bottom]). */
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

    /** A 20 by 20 body centered on ([x], [y]), so its feet are at y + 10. */
    private class Hero(val transform: Transform, val body: KinematicBody) {
        val feet: Float get() = transform.y + 10f
        val left: Float get() = transform.x - 10f
        val right: Float get() = transform.x + 10f
        val top: Float get() = transform.y - 10f
    }

    private fun World.hero(x: Float, y: Float, mask: CollisionLayers = CollisionLayers.ALL): Hero {
        val entity = create()
        val transform = Transform(x = x, y = y)
        val body = KinematicBody()
        add(entity, Transform, transform)
        add(entity, Collider, Collider(Shape.Box(width = 20f, height = 20f), mask = mask))
        add(entity, KinematicBody, body)
        return Hero(transform, body)
    }

    /** Runs [steps] fixed steps, each adding gravity to the body's dy first, as a game's FIXED_UPDATE system does. */
    private fun Collisions.run(world: World, hero: Hero, steps: Int) {
        repeat(steps) {
            hero.body.dy += 1800f * step
            moveBodies(world, step, scenes = null)
        }
    }

    @Test
    fun aBodyStartingInsideASolidIsPushedOutAndReportsIt() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 300f)
        // The feet are 6 units into the roof, so the shortest way out is 6 units up.
        val hero = world.hero(x = 100f, y = 96f)
        val collisions = Collisions()
        collisions.moveBodies(world, step, scenes = null)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertEquals(100f, hero.transform.x)
        assertTrue(hero.body.wasPushedOut)
        assertFalse(hero.body.isStuck)
        assertNull(collisions.sweepBox(x = hero.transform.x, y = hero.transform.y, width = 20f, height = 20f, dx = 0f, dy = 0f))
        assertEquals(
            "KinematicBody(dx=0.0, dy=0.0, isOnFloor=false, isOnWall=false, wallNormalX=0.0, isOnCeiling=false, wasPushedOut=true)",
            hero.body.toString(),
        )

        // The next move starts clear: nothing pushes, and the flag goes down again.
        collisions.moveBodies(world, step, scenes = null)
        assertFalse(hero.body.wasPushedOut)
        assertEquals(100f - gap, hero.feet, 0.0001f)
    }

    @Test
    fun aBodyIsPushedOutTheShortestWay() {
        fun pushedFrom(x: Float, y: Float): Hero {
            val world = World()
            world.solid(left = 0f, top = 0f, right = 200f, bottom = 200f)
            val hero = world.hero(x, y)
            Collisions().moveBodies(world, step, scenes = null)
            assertTrue(hero.body.wasPushedOut, "The body at ($x, $y) was not pushed.")
            return hero
        }
        // Five units from a face, so the box is 15 units into the block and that face is nearest.
        assertEquals(-gap, pushedFrom(x = 100f, y = 5f).feet, 0.0001f)
        assertEquals(200f + gap, pushedFrom(x = 100f, y = 195f).top, 0.0001f)
        assertEquals(-gap, pushedFrom(x = 5f, y = 100f).right, 0.0001f)
        assertEquals(200f + gap, pushedFrom(x = 195f, y = 100f).left, 0.0001f)
        // From the middle every way is 110 units, and up wins the tie.
        val middle = pushedFrom(x = 100f, y = 100f)
        assertEquals(-gap, middle.feet, 0.0001f)
        assertEquals(100f, middle.transform.x)
    }

    @Test
    fun aOneWayLedgeNeverPushesABodyThatOverlapsIt() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f, passable = Passable.FROM_BELOW)
        // Half in the ledge: a wall would push the box out, a ledge never does.
        val hero = world.hero(x = 100f, y = 105f)
        Collisions().moveBodies(world, step, scenes = null)
        assertEquals(105f, hero.transform.y)
        assertFalse(hero.body.wasPushedOut)
        assertFalse(hero.body.isStuck)
    }

    @Test
    fun aOneWayLedgeInTheWayOfAPushDoesNotBlockIt() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        world.solid(left = 0f, top = 85f, right = 200f, bottom = 95f, passable = Passable.FROM_BELOW)
        // Five units into the floor, and the push up ends inside the ledge, which counts for nothing.
        val hero = world.hero(x = 100f, y = 95f)
        Collisions().moveBodies(world, step, scenes = null)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.wasPushedOut)
        assertFalse(hero.body.isStuck)
    }

    @Test
    fun aSolidOutsideTheBodysMaskDoesNotPush() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 300f, layers = CollisionLayers.of(3))
        val hero = world.hero(x = 100f, y = 96f, mask = CollisionLayers.DEFAULT)
        Collisions().moveBodies(world, step, scenes = null)
        assertEquals(96f, hero.transform.y)
        assertFalse(hero.body.wasPushedOut)
        assertFalse(hero.body.isStuck)
    }

    @Test
    fun aBodyInACornerIsPushedOutOfBothSolids() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 140f)
        world.solid(left = 0f, top = 0f, right = 50f, bottom = 100f)
        // Five units into the wall's face and five into the floor: one push for each.
        val hero = world.hero(x = 55f, y = 95f)
        Collisions().moveBodies(world, step, scenes = null)
        assertEquals(50f + gap, hero.left, 0.0001f)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.wasPushedOut)
        assertFalse(hero.body.isStuck)
    }

    @Test
    fun aBodyWithAnOffsetColliderIsPushedOutByItsOwnBox() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        val entity = world.create()
        // The 20 by 20 box hangs 10 below the transform and ignores the transform's scale of 2.
        val transform = Transform(x = 100f, y = 85f, scaleX = 2f, scaleY = 2f)
        val collider = Collider(Shape.Box(width = 20f, height = 20f), offsetX = 0f, offsetY = 10f)
        collider.scalesWithTransform = false
        val body = KinematicBody()
        world.add(entity, Transform, transform)
        world.add(entity, Collider, collider)
        world.add(entity, KinematicBody, body)
        Collisions().moveBodies(world, step, scenes = null)
        // The box spans 85 to 105, so its feet are 5 into the floor and the transform moves 5 up.
        assertEquals(85f - 5f - gap, transform.y, 0.0001f)
        assertEquals(100f, transform.x)
        assertTrue(body.wasPushedOut)
    }

    @Test
    fun aBodyOverTheSeamOfTwoSolidsIsPushedOutOfBoth() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 64f, bottom = 140f)
        world.solid(left = 64f, top = 100f, right = 128f, bottom = 140f)
        // Pushing right to clear the first tile would enter the second; up clears both.
        val hero = world.hero(x = 64f, y = 95f)
        Collisions().moveBodies(world, step, scenes = null)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertEquals(64f, hero.transform.x)
        assertTrue(hero.body.wasPushedOut)
    }

    @Test
    fun aBodySqueezedBetweenTwoSolidsIsLeftWhereItWasAndStuck() {
        val world = World()
        // The gap is 16 wide and the body 20, so it is 2 units into each wall and every push undoes the last.
        world.solid(left = -50f, top = 0f, right = -8f, bottom = 400f)
        world.solid(left = 8f, top = 0f, right = 50f, bottom = 400f)
        val hero = world.hero(x = 0f, y = 200f)
        val collisions = Collisions()
        collisions.moveBodies(world, step, scenes = null)
        assertEquals(0f, hero.transform.x)
        assertEquals(200f, hero.transform.y)
        assertTrue(hero.body.isStuck)
        assertFalse(hero.body.wasPushedOut)

        // The move goes on from there, and the walls it overlaps do not stop it.
        hero.body.dy = 600f
        collisions.moveBodies(world, step, scenes = null)
        assertEquals(210f, hero.transform.y, 0.001f)
        assertTrue(hero.body.isStuck)
        assertEquals(
            "KinematicBody(dx=0.0, dy=600.0, isOnFloor=false, isOnWall=false, wallNormalX=0.0, isOnCeiling=false, isStuck=true)",
            hero.body.toString(),
        )

        // Between a floor and a ceiling 12 apart, the same.
        val tunnel = World()
        tunnel.solid(left = 0f, top = 96f, right = 200f, bottom = 140f)
        tunnel.solid(left = 0f, top = 40f, right = 200f, bottom = 84f)
        val crawler = tunnel.hero(x = 100f, y = 90f)
        Collisions().moveBodies(tunnel, step, scenes = null)
        assertEquals(90f, crawler.transform.y)
        assertTrue(crawler.body.isStuck)
    }

    @Test
    fun aSolidMovedOntoABodyPushesItOutAtTheNextMove() {
        val world = World()
        world.solid(left = 0f, top = 300f, right = 400f, bottom = 340f)
        val crate = world.solid(left = 300f, top = 250f, right = 340f, bottom = 300f)
        val hero = world.hero(x = 100f, y = 290f - gap)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 2)
        assertTrue(hero.body.isOnFloor)
        assertFalse(hero.body.wasPushedOut)

        // The crate's left edge ends 6 units inside the body's right side.
        world.get(crate, Transform).x -= 196f
        collisions.run(world, hero, steps = 1)
        assertTrue(hero.body.wasPushedOut)
        assertEquals(104f - gap, hero.right, 0.0001f)
        assertTrue(hero.body.isOnFloor)
        collisions.run(world, hero, steps = 1)
        assertFalse(hero.body.wasPushedOut)
        assertFalse(hero.body.isStuck)
    }

    @Test
    fun aBodyPlacedInAFloorStandsOnItAfterThePushAndLandsOnce() {
        val world = World()
        val floor = world.solid(left = 0f, top = 100f, right = 400f, bottom = 140f)
        val hero = world.hero(x = 100f, y = 96f)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 1)
        assertTrue(hero.body.wasPushedOut)
        assertTrue(hero.body.isOnFloor)
        assertTrue(hero.body.hasLanded)
        assertEquals(floor, hero.body.floorOrNull)
        assertEquals(100f - gap, hero.feet, 0.0001f)

        collisions.run(world, hero, steps = 1)
        assertFalse(hero.body.wasPushedOut)
        assertFalse(hero.body.hasLanded)
        assertTrue(hero.body.isOnFloor)
    }
}
