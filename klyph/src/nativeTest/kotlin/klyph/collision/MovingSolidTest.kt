package klyph.collision

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.entity.World
import klyph.entity.applyVelocities
import klyph.event.Events
import klyph.scene.NO_ENTITY
import klyph.scene.SceneHarness
import klyph.scene.SceneStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Solids that move carry the bodies standing on them and push the bodies they move into. */
class MovingSolidTest {
    private val step = 1f / 60f
    private val gap = Collisions.CONTACT_GAP

    /** Spawns a box solid from ([left], [top]) to ([right], [bottom]), moving at ([dx], [dy]) when either is not 0. */
    private fun World.solid(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        dx: Float = 0f,
        dy: Float = 0f,
        passable: Passable = Passable.NEVER,
    ): Entity {
        val entity = create()
        add(entity, Transform, Transform(x = (left + right) / 2, y = (top + bottom) / 2))
        add(entity, Collider, Collider(Shape.Box(width = right - left, height = bottom - top)))
        add(entity, Solid, Solid(passable))
        if (dx != 0f || dy != 0f) add(entity, Velocity, Velocity(dx, dy))
        return entity
    }

    /** A 20 by 20 body centered on ([x], [y]), so its feet are at y + 10. */
    private class Hero(val entity: Entity, val transform: Transform, val body: KinematicBody) {
        val feet: Float get() = transform.y + 10f
        val top: Float get() = transform.y - 10f
        val left: Float get() = transform.x - 10f
        val right: Float get() = transform.x + 10f
    }

    private fun World.hero(x: Float, feet: Float): Hero {
        val entity = create()
        val transform = Transform(x = x, y = feet - 10f)
        val body = KinematicBody()
        add(entity, Transform, transform)
        add(entity, Collider, Collider(Shape.Box(width = 20f, height = 20f)))
        add(entity, KinematicBody, body)
        return Hero(entity, transform, body)
    }

    private fun World.topOf(solid: Entity): Float = get(solid, Transform).y - (get(solid, Collider).shape as Shape.Box).height / 2

    private fun World.leftOf(solid: Entity): Float = get(solid, Transform).x - (get(solid, Collider).shape as Shape.Box).width / 2

    /**
     * Runs [steps] fixed steps as the frame loop does: the systems, here gravity for [gravityOn],
     * then every `Velocity`, then the bodies' move.
     */
    private fun Collisions.run(world: World, steps: Int = 1, gravityOn: Hero? = null, scenes: SceneStack? = null) {
        repeat(steps) {
            if (gravityOn != null) gravityOn.body.dy += 1800f * step
            world.applyVelocities(step, scenes)
            moveBodies(world, step, scenes)
        }
    }

    /**
     * Lets [hero] come down onto what is under him before anything moves, as the first step of an
     * app does after the engine took note of the level when it started.
     */
    private fun Collisions.settle(world: World, hero: Hero, scenes: SceneStack? = null) {
        hero.body.dy += 1800f * step
        moveBodies(world, step, scenes)
    }

    @Test
    fun aBodyOnAPlatformMovingAcrossIsCarriedWithIt() {
        val world = World()
        val platform = world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dx = 60f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        assertTrue(hero.body.isOnFloor)
        val start = hero.transform.x

        collisions.run(world, steps = 30, gravityOn = hero)
        // Half a second at 60 units a second.
        assertEquals(start + 30f, hero.transform.x, 0.01f)
        assertTrue(hero.body.isOnFloor)
        assertTrue(hero.body.wasCarried)
        assertFalse(hero.body.wasPushedOut)
        assertFalse(hero.body.hasLanded)
        assertEquals(platform, hero.body.floorOrNull)
        assertEquals(world.topOf(platform) - gap, hero.feet, 0.0001f)
    }

    @Test
    fun aBodyOnARisingPlatformIsCarriedUp() {
        val world = World()
        val platform = world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dy = -60f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        collisions.run(world, steps = 30, gravityOn = hero)
        assertEquals(70f, world.topOf(platform), 0.01f)
        assertEquals(70f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.isOnFloor)
        assertTrue(hero.body.wasCarried)
        assertFalse(hero.body.wasPushedOut)
        assertEquals(100f, hero.transform.x)
    }

    @Test
    fun aBodyOnAPlatformGoingDownFasterThanTheFloorSnapStaysOnIt() {
        val world = World()
        // Five units a step, more than the two of the floor snap.
        val platform = world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dy = 300f)
        assertTrue(300f * step > Collisions.FLOOR_SNAP_DISTANCE)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        repeat(30) {
            collisions.run(world, gravityOn = hero)
            assertTrue(hero.body.isOnFloor, "Off the floor in step $it.")
            assertFalse(hero.body.hasLanded, "Landed again in step $it.")
            assertEquals(world.topOf(platform) - gap, hero.feet, 0.0001f)
        }
    }

    @Test
    fun aBodyTouchingTheSideOfAPlatformIsNotCarried() {
        val world = World()
        world.solid(left = -100f, top = 100f, right = 400f, bottom = 140f)
        // A lift beside the hero, its right side a contact gap from his left, going up and down.
        val lift = world.solid(left = 0f, top = 60f, right = 90f, bottom = 100f, dy = -60f)
        val hero = world.hero(x = 100f + gap, feet = 100f - gap)
        val collisions = Collisions()
        collisions.run(world, steps = 20, gravityOn = hero)
        world.get(lift, Velocity).dy = 60f
        collisions.run(world, steps = 40, gravityOn = hero)
        assertEquals(100f + gap, hero.transform.x)
        assertEquals(100f - gap, hero.feet, 0.0001f)
        assertFalse(hero.body.wasCarried)
        assertFalse(hero.body.wasPushedOut)
    }

    @Test
    fun aSolidMovingAcrossIntoABodyPushesItAlong() {
        val world = World()
        world.solid(left = -400f, top = 100f, right = 400f, bottom = 140f)
        val wall = world.solid(left = 150f, top = 0f, right = 200f, bottom = 100f, dx = -120f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        // The wall closes the 40 units in 20 steps and then pushes the hero 2 units a step.
        collisions.run(world, steps = 30, gravityOn = hero)
        assertEquals(world.leftOf(wall) - gap, hero.right, 0.001f)
        assertEquals(90f - gap, hero.right, 0.01f)
        assertTrue(hero.body.wasPushedOut)
        assertFalse(hero.body.isCrushed)
        assertTrue(hero.body.isOnFloor)
    }

    @Test
    fun aRisingFloorPushesUpABodyAboveIt() {
        val world = World()
        val floor = world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dy = -120f)
        // Hovering one unit above it, with no gravity, so only the floor moves him.
        val hero = world.hero(x = 100f, feet = 99f)
        val collisions = Collisions()
        collisions.run(world, steps = 5)
        assertEquals(90f, world.topOf(floor), 0.01f)
        assertEquals(world.topOf(floor) - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.wasPushedOut)
        assertFalse(hero.body.wasCarried)
    }

    @Test
    fun aBodyLiftedIntoACeilingIsCrushedAndStaysUnderIt() {
        val world = World()
        world.solid(left = 0f, top = 0f, right = 200f, bottom = 70f)
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dy = -240f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        // Settled on the lift, then carried 4 units a step with no gravity, so only the lift moves him.
        collisions.settle(world, hero)
        var steps = 0
        while (!hero.body.isCrushed && steps < 20) {
            collisions.run(world)
            steps++
        }
        assertTrue(hero.body.isCrushed)
        assertEquals(70f + gap, hero.top, 0.0001f)
        assertFalse(hero.body.wasPushedOut)
        assertFalse(hero.body.isStuck)
        assertTrue(hero.body.toString().endsWith("wasCarried=true, isCrushed=true)"), hero.body.toString())

        collisions.run(world)
        assertTrue(hero.body.isCrushed)
        assertEquals(70f + gap, hero.top, 0.0001f)
    }

    @Test
    fun floorVelocityIsTheFloorsMoveOverTheStepAndZeroOffIt() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 120f, dx = 90f, dy = -30f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        collisions.run(world, gravityOn = hero)
        assertEquals(90f, hero.body.floorVelocityX, 0.01f)
        assertEquals(-30f, hero.body.floorVelocityY, 0.01f)
        assertTrue(hero.body.toString().contains(", floorVelocityX="), hero.body.toString())

        // A jump: the speed stays readable after dy is set upward, and goes to 0 once he is off the floor.
        hero.body.dy = -600f
        assertEquals(90f, hero.body.floorVelocityX, 0.01f)
        collisions.run(world, gravityOn = hero)
        assertFalse(hero.body.isOnFloor)
        assertFalse(hero.body.wasCarried)
        assertEquals(0f, hero.body.floorVelocityX)
        assertEquals(0f, hero.body.floorVelocityY)
    }

    @Test
    fun aStillFloorHasNoFloorVelocity() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 120f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.run(world, steps = 3, gravityOn = hero)
        assertTrue(hero.body.isOnFloor)
        assertEquals(0f, hero.body.floorVelocityX)
        assertEquals(0f, hero.body.floorVelocityY)
        assertFalse(hero.body.wasCarried)
    }

    @Test
    fun aOneWayLedgeRisingThroughABodyDoesNotPushItAndTheBodyLandsOnIt() {
        val world = World()
        val ledge = world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f, dy = -60f, passable = Passable.FROM_BELOW)
        // His feet are 5 units into the ledge, and he jumps up out of it.
        val hero = world.hero(x = 100f, feet = 105f)
        hero.body.dy = -300f
        val collisions = Collisions()
        var steps = 0
        while (!hero.body.isOnFloor && steps < 120) {
            collisions.run(world, gravityOn = hero)
            assertFalse(hero.body.wasPushedOut, "Pushed in step $steps.")
            assertFalse(hero.body.isCrushed, "Crushed in step $steps.")
            steps++
        }
        assertTrue(hero.body.isOnFloor)
        assertEquals(ledge, hero.body.floorOrNull)
        collisions.run(world, gravityOn = hero)
        assertTrue(hero.body.wasCarried)
        assertEquals(world.topOf(ledge) - gap, hero.feet, 0.0001f)
    }

    @Test
    fun twoFixedStepsInOneFrameCarryTwice() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 120f, dx = 60f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        val events = Events()
        collisions.settle(world, hero)
        collisions.detect(world, events)
        val start = hero.transform.x
        // Two steps in one frame, then the frame's detection, then one step in the next frame.
        collisions.run(world, steps = 2, gravityOn = hero)
        assertEquals(start + 2f, hero.transform.x, 0.001f)
        collisions.detect(world, events)
        collisions.run(world, gravityOn = hero)
        assertEquals(start + 3f, hero.transform.x, 0.001f)
    }

    @Test
    fun aSolidMovedByASystemCarriesToo() {
        val world = World()
        val platform = world.solid(left = 0f, top = 100f, right = 400f, bottom = 120f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.run(world, gravityOn = hero)

        // A system moves it 3 across and 2 down; detection in between does not use the move up.
        val transform = world.get(platform, Transform)
        transform.x += 3f
        transform.y += 2f
        collisions.detect(world, Events())
        collisions.run(world, gravityOn = hero)
        assertEquals(103f, hero.transform.x, 0.0001f)
        assertEquals(102f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.wasCarried)
        assertEquals(3f / step, hero.body.floorVelocityX, 0.1f)

        // Still again: no carry, and sweeps keep finding it.
        collisions.run(world, steps = 200, gravityOn = hero)
        assertFalse(hero.body.wasCarried)
        assertEquals(103f, hero.transform.x, 0.0001f)
        assertEquals(platform, collisions.sweepBox(x = 300f, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 200f)?.entity)
    }

    @Test
    fun aRiderIsCarriedWhenAnotherSolidIsRemovedInTheSameStep() {
        val world = World()
        val other = world.solid(left = 1000f, top = 100f, right = 1200f, bottom = 120f)
        world.solid(left = 0f, top = 100f, right = 400f, bottom = 120f, dx = 60f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        val start = hero.transform.x
        // Removing the first solid moves the platform to another place in the store.
        world.destroy(other)
        collisions.run(world, gravityOn = hero)
        assertEquals(start + 1f, hero.transform.x, 0.001f)
        assertTrue(hero.body.wasCarried)
    }

    @Test
    fun sweepsFindAMovingSolidWhereItIsNow() {
        val world = World()
        // Static floor tiles fill the grid; the platform moving over them is tested on its own.
        for (i in 0 until 20) world.solid(left = i * 64f, top = 400f, right = i * 64f + 64f, bottom = 464f)
        val platform = world.solid(left = 0f, top = 100f, right = 100f, bottom = 120f, dx = 600f)
        val collisions = Collisions()
        repeat(10) {
            collisions.run(world)
            val x = world.get(platform, Transform).x
            assertEquals(platform, collisions.sweepBox(x = x, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 200f)?.entity)
            assertNull(collisions.sweepBox(x = x - 80f, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 200f))
        }
        assertEquals(21, collisions.solidCount)
    }

    @Test
    fun aFrozenBodyIsNeitherCarriedNorPushed() {
        val h = SceneHarness()
        lateinit var rider: Hero
        lateinit var neighbour: Hero
        val play = h.scene(
            "play",
            enter = {
                rider = h.world.hero(x = 100f, feet = 100f - gap)
                neighbour = h.world.hero(x = 400f, feet = 100f - gap)
            },
        )
        var wall = NO_ENTITY
        val pause = h.scene(
            "pause",
            enter = {
                h.world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dx = 60f)
                h.world.solid(left = 300f, top = 100f, right = 600f, bottom = 120f)
                wall = h.world.solid(left = 420f, top = 0f, right = 460f, bottom = 100f, dx = -120f)
            },
        )
        h.stack.push(play)
        h.applyTransitions()
        h.stack.push(pause)
        h.applyTransitions()
        val collisions = Collisions()
        collisions.run(h.world, steps = 30, scenes = h.stack)
        assertEquals(100f, rider.transform.x)
        assertEquals(400f, neighbour.transform.x)
        assertFalse(rider.body.wasCarried)
        assertFalse(neighbour.body.wasPushedOut)
        assertTrue(h.world.leftOf(wall) < neighbour.right, "The wall moved into the frozen body.")
    }

    @Test
    fun aSolidWithAVelocityInAFrozenSceneDoesNotMove() {
        val h = SceneHarness()
        var platform = NO_ENTITY
        h.stack.push(h.scene("play", enter = { platform = h.world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dx = 60f) }))
        h.applyTransitions()
        h.stack.push(h.scene("pause"))
        h.applyTransitions()
        val collisions = Collisions()
        collisions.run(h.world, steps = 10, scenes = h.stack)
        assertEquals(100f, h.world.get(platform, Transform).x)
        assertEquals(0, collisions.solidCount)

        h.stack.pop()
        h.applyTransitions()
        collisions.run(h.world, steps = 1, scenes = h.stack)
        assertEquals(101f, h.world.get(platform, Transform).x, 0.001f)
        assertNotNull(collisions.sweepBox(x = 101f, y = 0f, width = 10f, height = 10f, dx = 0f, dy = 200f))
    }

    @Test
    fun aSolidMovingDiagonallyPastABodyDoesNotPushIt() {
        val world = World()
        val block = world.solid(left = 0f, top = 0f, right = 50f, bottom = 50f)
        // The union of the block's old and new boxes covers him, but the block passes below his
        // bottom-left corner: when its right side reaches his left side, its top is already below him.
        val hero = world.hero(x = 85f, feet = 20f)
        val collisions = Collisions()
        collisions.run(world)
        val transform = world.get(block, Transform)
        transform.x += 50f
        transform.y += 50f
        collisions.run(world)
        assertEquals(85f, hero.transform.x)
        assertEquals(20f, hero.feet)
        assertFalse(hero.body.wasPushedOut)
    }

    @Test
    fun aSolidMovingDiagonallyThroughABodyPushesIt() {
        val world = World()
        val block = world.solid(left = 0f, top = 0f, right = 50f, bottom = 50f)
        val hero = world.hero(x = 65f, feet = 20f)
        val collisions = Collisions()
        collisions.run(world)
        val transform = world.get(block, Transform)
        transform.x += 50f
        transform.y += 50f
        collisions.run(world)
        assertEquals(100f + gap, hero.left, 0.0001f)
        assertTrue(hero.body.wasPushedOut)
        assertFalse(hero.body.isCrushed)
    }

    @Test
    fun aOneWayLiftCarryingABodyIntoACeilingLetsItDropThrough() {
        val world = World()
        world.solid(left = 0f, top = 0f, right = 200f, bottom = 70f)
        val ledge = world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f, dy = -240f, passable = Passable.FROM_BELOW)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        // Four units a step: his head reaches the ceiling in the third step, the ledge goes on to 76.
        repeat(6) {
            collisions.run(world, gravityOn = hero)
            assertFalse(hero.body.isCrushed, "Crushed in step $it.")
            assertFalse(hero.body.wasPushedOut, "Pushed in step $it.")
        }
        // Stopped by the ceiling, the ledge rose past his feet, and he has started to fall.
        assertTrue(hero.feet > world.topOf(ledge), "His feet ${hero.feet} are above the ledge.")
        assertFalse(hero.body.isOnFloor)
        world.get(ledge, Velocity).dy = 0f
        collisions.run(world, steps = 30, gravityOn = hero)
        assertFalse(hero.body.isCrushed)
        assertTrue(hero.top > world.topOf(ledge) + 10f, "His top ${hero.top} is not below the ledge.")
    }

    @Test
    fun aBodyJumpingOffARisingLiftMovesByItsOwnSpeedOnly() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dy = -240f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        collisions.run(world, gravityOn = hero)
        assertEquals(-240f, hero.body.floorVelocityY, 0.01f)

        hero.body.dy = -600f + hero.body.floorVelocityY
        val expected = hero.transform.y + hero.body.dy * step
        collisions.run(world)
        assertEquals(expected, hero.transform.y, 0.0001f)
        assertFalse(hero.body.wasCarried)
        assertFalse(hero.body.wasPushedOut)
        assertFalse(hero.body.isCrushed)
    }

    @Test
    fun aBodyJumpingOffARisingLiftUnderACeilingIsNotCrushed() {
        val world = World()
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f, dy = -240f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        collisions.run(world, gravityOn = hero)
        // Three units above his head, less than the lift's four a step.
        val ceiling = hero.top - 3f
        world.solid(left = 0f, top = 0f, right = 200f, bottom = ceiling)

        hero.body.dy = -600f + hero.body.floorVelocityY
        collisions.run(world)
        assertFalse(hero.body.isCrushed)
        assertFalse(hero.body.wasPushedOut)
        assertTrue(hero.body.isOnCeiling)
        assertEquals(ceiling + gap, hero.top, 0.0001f)
    }

    @Test
    fun aSolidWhoseScaleGrowsCarriesNothing() {
        val world = World()
        val platform = world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        // Twice as wide about its center: its left edge moves 100 units, but it did not move.
        world.get(platform, Transform).scaleX = 2f
        collisions.run(world, gravityOn = hero)
        assertEquals(100f, hero.transform.x)
        assertFalse(hero.body.wasCarried)
        assertTrue(hero.body.isOnFloor)
    }

    @Test
    fun aBodyOnASolidThatGrowsUpwardIsPushedOutNotCarried() {
        val world = World()
        val platform = world.solid(left = 0f, top = 100f, right = 200f, bottom = 120f)
        val hero = world.hero(x = 100f, feet = 100f - gap)
        val collisions = Collisions()
        collisions.settle(world, hero)
        // Twice as tall with its bottom kept at 120: its top rises 20 units into him.
        val transform = world.get(platform, Transform)
        transform.scaleY = 2f
        transform.y = 100f
        collisions.run(world, gravityOn = hero)
        assertEquals(80f - gap, hero.feet, 0.0001f)
        assertTrue(hero.body.wasPushedOut)
        assertFalse(hero.body.wasCarried)
    }

    @Test
    fun aRefreshAgainstAnotherWorldShiftsNothing() {
        val first = World()
        val a = first.solid(left = 0f, top = 100f, right = 200f, bottom = 120f)
        val second = World()
        val b = second.solid(left = 50f, top = 140f, right = 250f, bottom = 160f)
        assertEquals(a, b)
        val grid = SolidGrid()
        grid.refresh(first, scenes = null, cellSize = 64f)
        grid.refresh(second, scenes = null, cellSize = 64f)
        val solid = grid.solidOf(b.raw)
        assertEquals(0f, grid.shiftXOf(solid))
        assertEquals(0f, grid.shiftYOf(solid))
        assertEquals(0, grid.shiftedCount)
    }
}
