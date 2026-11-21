package klyph.physics

import klyph.math.Vec2
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The hand: grabbing the nearest particle, dragging it, and never blowing a body up. */
class GrabTest {
    private val dt = TestBodies.DT

    /** A 10 x 10 room with a floor along its bottom row, and a dome standing on it in the middle. */
    private fun blobOnTheFloor(): Pair<PhysicsWorld, SoftBody> {
        val world = PhysicsWorld()
        world.solids = TestBodies.grid().apply { for (c in 0 until 10) fill(c, 9) }
        val body = world.softBody(TestBodies.dome(), 80f, 9 * TestBodies.TILE)
        repeat(60) { world.step(dt) }
        return world to body
    }

    @Test
    fun itGrabsTheNearestParticleThatCanMoveAndOnlyWithinReach() {
        val world = PhysicsWorld()
        val ps = world.particles
        val pinned = ps.add(100f, 100f, mass = Particles.STATIC)
        val near = ps.add(110f, 100f)
        ps.add(130f, 100f)
        world.grab(101f, 100f)
        assertEquals(near, world.hand.particleOrNull, "the static one is nearer, but can't move")
        assertEquals(near, world.hand.particleOrNull)
        world.release()
        world.grab(300f, 300f)
        assertNull(world.hand.particleOrNull, "nothing in reach")
        assertNull(world.hand.particleOrNull)
        assertFalse(world.hand.isHolding)
        assertTrue(ps.isAlive(pinned))
    }

    @Test
    fun aGrabbedParticleFollowsTheHand() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO))
        val p = world.particles.add(100f, 100f)
        world.grab(100f, 100f)
        world.moveGrab(180f, 140f)
        repeat(60) { world.step(dt) }
        assertEquals(180f, world.particles.xOf(p), 1f)
        assertEquals(140f, world.particles.yOf(p), 1f)
    }

    @Test
    fun grabbingTheTopOfABlobLiftsTheWholeBlob() {
        val (world, body) = blobOnTheFloor()
        val bodies = world.softBodies
        val bottom = bodies.maxYOf(body)
        val top = bodies.minYOf(body)
        val x = bodies.centerXOf(body)
        world.grab(x, top)
        assertNotNull(world.hand.particleOrNull)
        world.moveGrab(x, top - 60f)
        repeat(120) { world.step(dt) }
        assertTrue(bodies.maxYOf(body) < bottom - 40f, "it hangs off the floor: bottom ${bodies.maxYOf(body)}, was $bottom")
        assertTrue(bodies.maxEdgeStretchOf(body) < 1.6f, "and it holds together: ${bodies.maxEdgeStretchOf(body)}")
    }

    @Test
    fun flingingTheHandAboutNeverBlowsUpABody() {
        val (world, body) = blobOnTheFloor()
        val bodies = world.softBodies
        val rest = bodies.areaOf(body)
        world.grab(bodies.centerXOf(body), bodies.minYOf(body))
        val random = Random(3)
        // a mouse thrown from one side of the screen to the other every frame, and far off it
        repeat(300) {
            world.moveGrab(random.nextFloat() * 4000f - 2000f, random.nextFloat() * 4000f - 2000f)
            world.step(dt)
            assertTrue(bodies.isFinite(body), "still finite at step $it")
            assertTrue(bodies.maxEdgeStretchOf(body) < 2.5f, "not torn apart at step $it: ${bodies.maxEdgeStretchOf(body)}")
        }
        world.release()
        repeat(300) { world.step(dt) }
        assertTrue(bodies.maxEdgeStretchOf(body) < 1.1f, "back in shape once let go: ${bodies.maxEdgeStretchOf(body)}")
        assertTrue(abs(bodies.areaOf(body) / rest - 1f) < 0.1f, "and the same size: ${bodies.areaOf(body)} of $rest")
    }

    @Test
    fun lettingGoRemovesThePinAndGrabbingAgainLetsGoOfTheLast() {
        val world = PhysicsWorld()
        val a = world.particles.add(0f, 0f)
        val b = world.particles.add(50f, 0f)
        world.grab(0f, 0f)
        assertEquals(1, world.constraints.pinCount)
        world.grab(50f, 0f)
        assertEquals(b, world.hand.particleOrNull)
        assertEquals(1, world.constraints.pinCount, "one hand, one pin")
        world.release()
        assertEquals(0, world.constraints.pinCount)
        world.release()
        assertEquals(0, world.constraints.pinCount, "letting go twice is fine")
        assertTrue(world.particles.isAlive(a))
    }

    @Test
    fun aGrabbedParticleThatIsRemovedIsLetGo() {
        val world = PhysicsWorld()
        val p = world.particles.add(0f, 0f)
        world.grab(0f, 0f)
        world.particles.remove(p)
        world.moveGrab(100f, 0f)
        world.step(dt)
        assertNull(world.hand.particleOrNull)
        // and a new particle in its handle isn't dragged
        val q = world.particles.add(0f, 0f, mass = 1f)
        world.constraints.pin(q)
        world.moveGrab(100f, 0f)
        world.step(dt)
        assertEquals(0f, world.particles.xOf(q), 0.01f)
    }

    @Test
    fun aBoxGrabbedByItsCornerHangsFromThatCorner() {
        val world = PhysicsWorld()
        val bodies = world.rigidBodies
        val box = world.rigidBody(Shape.box(30f, 30f), 100f, 100f)
        // just inside its top-left corner
        world.grab(87f, 87f)
        assertNull(world.hand.particleOrNull, "a body, not a particle")
        assertEquals(box, world.hand.bodyOrNull)
        // it swings down, like a pendulum, until damping has stilled it with its centre straight under the corner held
        repeat(1200) { world.step(dt) }
        assertEquals(world.hand.heldX, bodies.xOf(box), 0.5f, "under the corner")
        assertTrue(bodies.yOf(box) > world.hand.heldY + 15f, "below it")
        assertEquals(87f, world.hand.heldX, 0.5f, "the corner stays in the hand")
        assertEquals(87f, world.hand.heldY, 0.5f)
        world.release()
        assertNull(world.hand.bodyOrNull)
    }

    @Test
    fun aGrabbedBodyFollowsTheHandAndAStaticOneCantBeGrabbed() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO))
        val bodies = world.rigidBodies
        val wall = world.rigidBody(Shape.box(20f, 20f), 300f, 300f, kind = BodyKind.STATIC)
        world.grab(300f, 300f)
        assertFalse(world.hand.isHolding, "the static wall stays where it is: $wall")
        val heavy = world.rigidBody(Shape.circle(20f), 100f, 100f, density = 5f)
        world.grab(100f, 100f)
        world.moveGrab(200f, 150f)
        repeat(60) { world.step(dt) }
        assertEquals(200f, bodies.xOf(heavy), 1f, "a heavy body follows as well as a light one")
        assertEquals(150f, bodies.yOf(heavy), 1f)
    }

    @Test
    fun aGrabbedBodyThatIsRemovedIsLetGoBeforeItsSlotIsReused() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO))
        val bodies = world.rigidBodies
        val box = world.rigidBody(Shape.box(20f, 20f), 100f, 100f)
        world.grab(100f, 100f)
        assertEquals(box, world.hand.bodyOrNull)
        bodies.remove(box)
        assertNull(world.hand.bodyOrNull)
        val reused = world.rigidBody(Shape.box(20f, 20f), 200f, 200f)
        assertEquals(box, reused)
        world.moveGrab(400f, 400f)
        repeat(10) { world.step(dt) }
        assertEquals(200f, bodies.xOf(reused), 0.01f)
        assertEquals(200f, bodies.yOf(reused), 0.01f)
    }
}
