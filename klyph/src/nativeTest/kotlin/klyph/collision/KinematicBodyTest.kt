package klyph.collision

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.entity.World
import klyph.event.Events
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class KinematicBodyTest {
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
    private class Hero(val entity: Entity, val transform: Transform, val body: KinematicBody) {
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
        return Hero(entity, transform, body)
    }

    /** Runs [steps] fixed steps, each adding [gravity] to the body's dy first, as a game's FIXED_UPDATE system does. */
    private fun Collisions.run(world: World, hero: Hero, steps: Int, gravity: Float = 1800f, dx: Float? = null) {
        repeat(steps) {
            hero.body.dy += gravity * step
            if (dx != null) hero.body.dx = dx
            moveBodies(world, step, scenes = null)
        }
    }

    @Test
    fun landsOnAFloorFromAFall() {
        val world = World()
        val floor = world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        val hero = world.hero(x = 100f, y = 20f)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 60)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertEquals(0f, hero.body.dy)
        assertTrue(hero.body.isOnFloor)
        assertFalse(hero.body.isOnWall)
        assertFalse(hero.body.isOnCeiling)
        assertEquals(floor, hero.body.floorOrNull)
    }

    @Test
    fun anEmptyWorldLetsTheBodyFall() {
        val world = World()
        val hero = world.hero(x = 0f, y = 0f)
        hero.body.dy = 600f
        Collisions().moveBodies(world, 0.5.seconds)
        assertEquals(300f, hero.transform.y)
        assertFalse(hero.body.isOnFloor)
        assertNull(hero.body.floorOrNull)
    }

    @Test
    fun stopsAtAWallAndReportsTheSide() {
        val world = World()
        world.solid(left = 200f, top = 0f, right = 240f, bottom = 400f)
        world.solid(left = -40f, top = 0f, right = 0f, bottom = 400f)
        val hero = world.hero(x = 150f, y = 200f)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 20, gravity = 0f, dx = 600f)
        assertEquals(200f - gap, hero.right, 0.0001f)
        assertEquals(0f, hero.body.dx)
        assertTrue(hero.body.isOnWall)
        assertEquals(-1f, hero.body.wallNormalX)

        collisions.run(world, hero, steps = 40, gravity = 0f, dx = -600f)
        assertEquals(gap, hero.left, 0.0001f)
        assertTrue(hero.body.isOnWall)
        assertEquals(1f, hero.body.wallNormalX)

        // No push into the wall, no wall.
        collisions.run(world, hero, steps = 1, gravity = 0f, dx = 0f)
        assertFalse(hero.body.isOnWall)
        assertEquals(0f, hero.body.wallNormalX)
    }

    @Test
    fun bumpsACeiling() {
        val world = World()
        world.solid(left = 0f, top = 0f, right = 200f, bottom = 20f)
        val hero = world.hero(x = 100f, y = 80f)
        hero.body.dy = -1200f
        val collisions = Collisions()
        collisions.moveBodies(world, step, scenes = null)
        collisions.moveBodies(world, step, scenes = null)
        collisions.moveBodies(world, step, scenes = null)
        assertEquals(20f + gap, hero.top, 0.0001f)
        assertEquals(0f, hero.body.dy)
        assertTrue(hero.body.isOnCeiling)
        assertFalse(hero.body.isOnFloor)
    }

    @Test
    fun slidesAlongAFloorWhilePressingIntoAWall() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 140f)
        world.solid(left = 300f, top = 0f, right = 340f, bottom = 100f)
        val hero = world.hero(x = 100f, y = 90f - gap)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 2, dx = 300f)
        // Walking on the floor never counts as running into it.
        assertTrue(hero.body.isOnFloor)
        assertFalse(hero.body.isOnWall)
        assertEquals(100f + 2 * 5f, hero.transform.x, 0.001f)
        collisions.run(world, hero, steps = 60, dx = 300f)
        assertEquals(300f - gap, hero.right, 0.0001f)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.isOnFloor)
        assertTrue(hero.body.isOnWall)
    }

    @Test
    fun oneWayLedgeIsPassedFromBelowAndFromTheSidesAndLandedOnFromAbove() {
        val world = World()
        val ledge = world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f, passable = Passable.FROM_BELOW)
        val hero = world.hero(x = 100f, y = 160f)
        hero.body.dy = -900f
        val collisions = Collisions()
        collisions.run(world, hero, steps = 10, gravity = 1800f)
        assertFalse(hero.body.isOnCeiling)
        assertTrue(hero.feet < 100f, "The jump took the feet up through the ledge, to ${hero.feet}.")
        collisions.run(world, hero, steps = 60)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.isOnFloor)
        assertEquals(ledge, hero.body.floorOrNull)

        // From the side, at the ledge's height, it is no wall.
        val walker = world.hero(x = -100f, y = 105f)
        collisions.run(world, walker, steps = 30, gravity = 0f, dx = 600f)
        assertFalse(walker.body.isOnWall)
        assertEquals(200f, walker.transform.x, 0.01f)
    }

    @Test
    fun dropThroughFallsOffALedgeOntoTheGroundBelow() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f, passable = Passable.FROM_BELOW)
        val ground = world.solid(left = -100f, top = 300f, right = 300f, bottom = 340f)
        val hero = world.hero(x = 100f, y = 90f - gap)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 2)
        assertTrue(hero.body.isOnFloor)

        hero.body.dropThrough()
        collisions.run(world, hero, steps = 1)
        assertFalse(hero.body.isOnFloor)
        collisions.run(world, hero, steps = 60)
        assertEquals(300f - gap, hero.feet, 0.0001f)
        assertEquals(ground, hero.body.floorOrNull)
    }

    @Test
    fun dropThroughIgnoresNoSolidThatIsNotOneWay() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        val hero = world.hero(x = 100f, y = 90f - gap)
        val collisions = Collisions()
        hero.body.dropThrough()
        collisions.run(world, hero, steps = 10)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.isOnFloor)
    }

    @Test
    fun floorSnapKeepsTheBodyOnASmallStepDown() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 100f, bottom = 140f)
        val lower = world.solid(left = 100f, top = 101f, right = 400f, bottom = 140f)
        val hero = world.hero(x = 80f, y = 90f - gap)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 1)
        assertTrue(hero.body.isOnFloor)
        // No gravity at all: only the snap can put the body on the lower floor.
        collisions.run(world, hero, steps = 30, gravity = 0f, dx = 120f)
        assertTrue(hero.left > 100f)
        assertTrue(hero.body.isOnFloor)
        assertEquals(lower, hero.body.floorOrNull)
        assertEquals(101f - gap, hero.feet, 0.0001f)
    }

    @Test
    fun floorSnapDoesNotReachALargerStepDown() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 100f, bottom = 140f)
        world.solid(left = 100f, top = 105f, right = 400f, bottom = 140f)
        val hero = world.hero(x = 80f, y = 90f - gap)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 1)
        collisions.run(world, hero, steps = 30, gravity = 0f, dx = 120f)
        assertFalse(hero.body.isOnFloor)
        assertEquals(100f - gap, hero.feet, 0.0001f)
    }

    @Test
    fun aJumpLeavesTheFloorWithoutASnap() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        val hero = world.hero(x = 100f, y = 90f - gap)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 1)
        hero.body.dy = -60f
        collisions.moveBodies(world, step, scenes = null)
        assertFalse(hero.body.isOnFloor)
        assertEquals(100f - gap - 1f, hero.feet, 0.0001f)
    }

    @Test
    fun theContactGapKeepsTheBodyOutOfTheSolid() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        world.solid(left = 200f, top = 0f, right = 240f, bottom = 100f)
        val hero = world.hero(x = 150f, y = 0f)
        val collisions = Collisions()
        repeat(120) {
            collisions.run(world, hero, steps = 1, dx = 900f)
            val inside = collisions.sweepBox(x = hero.transform.x, y = hero.transform.y, width = 20f, height = 20f, dx = 0f, dy = 0f)
            assertNull(inside, "Step $it left the body inside a solid.")
        }
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertEquals(200f - gap, hero.right, 0.0001f)
        // The body and the solids only come close, so detection reports no overlap.
        val events = Events()
        collisions.detect(world, events)
        assertEquals(0, collisions.pairCount)
    }

    @Test
    fun aFastFallDoesNotTunnelThroughAThinFloor() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 101f)
        val hero = world.hero(x = 100f, y = 0f)
        hero.body.dy = 60_000f
        Collisions().moveBodies(world, step, scenes = null)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.isOnFloor)
    }

    @Test
    fun aBodyIgnoresSolidsOutsideItsMask() {
        val world = World()
        val water = CollisionLayers.of(3)
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f, layers = water)
        val floor = world.solid(left = 0f, top = 200f, right = 200f, bottom = 240f)
        val hero = world.hero(x = 100f, y = 0f, mask = CollisionLayers.DEFAULT)
        Collisions().run(world, hero, steps = 120)
        assertEquals(200f - gap, hero.feet, 0.0001f)
        assertEquals(floor, hero.body.floorOrNull)
    }

    @Test
    fun aBodyStartingInsideASolidMovesOutOfIt() {
        val world = World()
        world.solid(left = 0f, top = 0f, right = 200f, bottom = 200f)
        val hero = world.hero(x = 190f, y = 100f)
        Collisions().run(world, hero, steps = 10, gravity = 0f, dx = 600f)
        assertEquals(290f, hero.transform.x, 0.01f)
        assertFalse(hero.body.isOnWall)
    }

    @Test
    fun aBodyWithAVelocityIsAnError() {
        val world = World()
        val hero = world.hero(x = 0f, y = 0f)
        world.add(hero.entity, Velocity, Velocity(dx = 1f))
        val error = assertFailsWith<IllegalStateException> { Collisions().moveBodies(world, step, scenes = null) }
        assertEquals(
            "Entity ${hero.entity} has both a Velocity and a KinematicBody; give a body its speed through KinematicBody.dx and dy.",
            error.message,
        )
    }

    @Test
    fun aBodySetUpWronglyIsAnError() {
        val world = World()
        val round = world.create()
        world.add(round, Transform, Transform())
        world.add(round, Collider, Collider(Shape.Circle(4f)))
        world.add(round, KinematicBody, KinematicBody())
        val error = assertFailsWith<IllegalStateException> { Collisions().moveBodies(world, step, scenes = null) }
        assertEquals("Entity $round has a KinematicBody whose collider shape Circle(radius=4.0) is not a Box.", error.message)

        world.destroy(round)
        val bare = world.create()
        world.add(bare, Transform, Transform())
        world.add(bare, KinematicBody, KinematicBody())
        assertFailsWith<IllegalStateException> { Collisions().moveBodies(world, step, scenes = null) }

        world.destroy(bare)
        val parent = world.create()
        val child = world.hero(x = 0f, y = 0f)
        world.setParent(child.entity, parent)
        val parented = assertFailsWith<IllegalStateException> { Collisions().moveBodies(world, step, scenes = null) }
        assertEquals("Entity ${child.entity} has a KinematicBody and a parent, but a body must be a root entity.", parented.message)
    }

    @Test
    fun aSolidOnACircleIsAnError() {
        val world = World()
        val rock = world.create()
        world.add(rock, Transform, Transform())
        world.add(rock, Collider, Collider(Shape.Circle(8f)))
        world.add(rock, Solid, Solid())
        val collisions = Collisions()
        val error = assertFailsWith<IllegalStateException> { collisions.moveBodies(world, step, scenes = null) }
        assertEquals("Entity $rock has a Solid whose collider shape Circle(radius=8.0) is not a Box.", error.message)
        assertFailsWith<IllegalStateException> { collisions.detect(world, Events()) }

        world.remove(rock, Collider)
        val missing = assertFailsWith<IllegalStateException> { collisions.detect(world, Events()) }
        assertEquals("Entity $rock has a Solid but no Collider.", missing.message)
    }

    @Test
    fun aSolidWithAParentOrAVelocityIsAnError() {
        val world = World()
        val building = world.create()
        world.add(building, Transform, Transform(x = 100f, y = 200f))
        val roof = world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        world.setParent(roof, building)
        val collisions = Collisions()
        val parented = assertFailsWith<IllegalStateException> { collisions.moveBodies(world, step, scenes = null) }
        assertEquals("Entity $roof has a Solid and a parent, but a solid must be a root entity.", parented.message)
        assertFailsWith<IllegalStateException> { collisions.detect(world, Events()) }

        world.destroy(roof)
        val platform = world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        world.add(platform, Velocity, Velocity(dx = 30f))
        val moving = assertFailsWith<IllegalStateException> { collisions.moveBodies(world, step, scenes = null) }
        assertEquals("Entity $platform has both a Solid and a Velocity, and solids cannot move.", moving.message)
    }

    @Test
    fun aBodyWithAnOffsetColliderThatIgnoresScaleStopsByItsOwnBox() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        val entity = world.create()
        // Drawn at twice the size, but the 20 by 20 box hangs 10 below the transform at scale 1.
        val transform = Transform(x = 100f, y = 0f, scaleX = 2f, scaleY = 2f)
        val body = KinematicBody()
        world.add(entity, Transform, transform)
        world.add(entity, Collider, Collider(Shape.Box(width = 20f, height = 20f), offsetX = 0f, offsetY = 10f).apply { scalesWithTransform = false })
        world.add(entity, KinematicBody, body)
        val collisions = Collisions()
        repeat(60) {
            body.dy += 1800f * step
            collisions.moveBodies(world, step, scenes = null)
        }
        assertTrue(body.isOnFloor)
        // Feet at y + 10 + 10: the transform rests 20 above the floor, not 40 as a scaled box would.
        assertEquals(100f - gap - 20f, transform.y, 0.0001f)
    }

    @Test
    fun dropThroughInTheAirIgnoresOnlyLedgesAtTheFeet() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f, passable = Passable.FROM_BELOW)
        val lower = world.solid(left = 0f, top = 200f, right = 200f, bottom = 210f, passable = Passable.FROM_BELOW)
        val collisions = Collisions()

        // Feet above the ledge: the drop line is above its top, so the ledge still catches him.
        val above = world.hero(x = 100f, y = 80f)
        above.body.dropThrough()
        collisions.run(world, above, steps = 60)
        assertEquals(100f - gap, above.feet, 0.0001f)

        // Feet level with the ledge in mid-air: he drops through it and lands on the next one down.
        val level = world.hero(x = 100f, y = 90f)
        level.body.dropThrough()
        collisions.run(world, level, steps = 90)
        assertEquals(200f - gap, level.feet, 0.0001f)
        assertEquals(lower, level.body.floorOrNull)
    }

    @Test
    fun aSolidMovedByItsTransformStopsBodiesWhereItNowIs() {
        val world = World()
        val floor = world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        val hero = world.hero(x = 100f, y = 0f)
        val collisions = Collisions()
        collisions.moveBodies(world, step, scenes = null)
        assertEquals(1, collisions.solidCount)
        world.get(floor, Transform).y += 100f
        collisions.run(world, hero, steps = 120)
        assertEquals(200f - gap, hero.feet, 0.0001f)
    }

    @Test
    fun aSolidsStateChangeIsSeenAtTheNextMove() {
        val world = World()
        val ledge = world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f)
        val hero = world.hero(x = 100f, y = 135f)
        val collisions = Collisions()
        hero.body.dy = -600f
        collisions.moveBodies(world, step, scenes = null)
        collisions.moveBodies(world, step, scenes = null)
        assertTrue(hero.body.isOnCeiling)
        world.get(ledge, Solid).passable = Passable.FROM_BELOW
        hero.body.dy = -600f
        collisions.run(world, hero, steps = 10, gravity = 0f)
        assertTrue(hero.feet < 100f)
    }

    @Test
    fun isOnFloorTurnsFalseAsSoonAsDyIsSetUpward() {
        val world = World()
        val floor = world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        val hero = world.hero(x = 100f, y = 90f - gap)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 2)
        assertTrue(hero.body.isOnFloor)
        assertEquals(floor, hero.body.floorOrNull)

        // A jump set by the game, before any move: no longer standing.
        hero.body.dy = -600f
        assertFalse(hero.body.isOnFloor)
        assertNull(hero.body.floorOrNull)
        // Setting it back before the move stands again: the last move did end on the floor.
        hero.body.dy = 0f
        assertTrue(hero.body.isOnFloor)

        hero.body.dy = -600f
        collisions.moveBodies(world, step, scenes = null)
        assertFalse(hero.body.isOnFloor)
        assertTrue(hero.feet < 100f - gap)
    }

    @Test
    fun hasLandedIsTrueForExactlyOneMovePerLanding() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 140f)
        val hero = world.hero(x = 100f, y = 0f)
        val collisions = Collisions()
        var landings = 0
        var lastLandingSpeed = 0f
        repeat(240) {
            // Jump once, a second in, after the first landing.
            if (it == 60) {
                assertTrue(hero.body.isOnFloor)
                hero.body.dy = -600f
            }
            val speed = hero.body.dy + 1800f * step
            collisions.run(world, hero, steps = 1)
            if (hero.body.hasLanded) {
                landings++
                lastLandingSpeed = hero.body.landingSpeed
                assertEquals(speed, hero.body.landingSpeed)
                assertTrue(hero.body.isOnFloor)
                assertEquals(0f, hero.body.dy)
            } else {
                assertEquals(0f, hero.body.landingSpeed)
            }
        }
        assertEquals(2, landings)
        assertTrue(lastLandingSpeed > 500f, "A jump of 600 comes back down at about 600, not $lastLandingSpeed.")
        assertTrue(hero.body.toString().endsWith("isOnCeiling=false)"))
    }

    @Test
    fun walkingDownASmallStepIsNoLanding() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 100f, bottom = 140f)
        world.solid(left = 100f, top = 101f, right = 400f, bottom = 140f)
        val hero = world.hero(x = 80f, y = 90f - gap)
        val collisions = Collisions()
        collisions.run(world, hero, steps = 1)
        assertTrue(hero.body.hasLanded)
        repeat(30) {
            collisions.run(world, hero, steps = 1, gravity = 0f, dx = 120f)
            assertFalse(hero.body.hasLanded, "Step $it counted a landing.")
        }
        assertEquals(101f - gap, hero.feet, 0.0001f)
    }

    @Test
    fun toStringNamesTheSpeedAndTheContacts() {
        val body = KinematicBody(dx = 120f)
        assertEquals(
            "KinematicBody(dx=120.0, dy=0.0, isOnFloor=false, isOnWall=false, wallNormalX=0.0, isOnCeiling=false)",
            body.toString(),
        )
        assertEquals("Solid(passable=FROM_BELOW)", Solid(Passable.FROM_BELOW).toString())

        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        val hero = world.hero(x = 100f, y = 0f)
        hero.body.dy = 900f
        Collisions().moveBodies(world, 0.5.seconds)
        assertEquals(
            "KinematicBody(dx=0.0, dy=0.0, isOnFloor=true, isOnWall=false, wallNormalX=0.0, isOnCeiling=false, " +
                "hasLanded=true, landingSpeed=900.0)",
            hero.body.toString(),
        )
    }
}
