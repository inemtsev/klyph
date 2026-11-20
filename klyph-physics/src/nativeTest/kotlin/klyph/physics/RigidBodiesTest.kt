package klyph.physics

import klyph.math.Vec2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RigidBodiesTest {
    private val dt = TestBodies.DT

    /** No gravity and no damping: nothing should change a body's motion but the solver. */
    private fun space() = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO, damping = 0f))

    @Test
    fun aSpinningBodyKeepsItsAngularVelocityWithoutGravity() {
        val world = space()
        val bodies = world.rigidBodies
        val b = bodies.add(100f, 100f, inertia = 50f)
        bodies.setAngularVelocity(b, 3f)
        repeat(600) { world.step(dt) }
        assertEquals(3f, bodies.angularVelocityOf(b), 1e-4f)
        assertEquals(3f * 600 * dt, bodies.angleOf(b), 1e-2f, "turned 3 rad/s for ten seconds")
        assertEquals(100f, bodies.xOf(b), 1e-4f, "spinning in place")
        assertEquals(100f, bodies.yOf(b), 1e-4f)
    }

    @Test
    fun aMovingBodyKeepsGoingInAStraightLine() {
        val world = space()
        val bodies = world.rigidBodies
        val b = bodies.add(0f, 0f)
        bodies.setVelocity(b, 60f, -30f)
        repeat(120) { world.step(dt) }
        assertEquals(120f, bodies.xOf(b), 0.01f)
        assertEquals(-60f, bodies.yOf(b), 0.01f)
        assertEquals(60f, bodies.velocityXOf(b), 1e-3f)
    }

    @Test
    fun aDynamicBodyFallsLikeAParticle() {
        val world = PhysicsWorld(PhysicsConfig(damping = 0f))
        val b = world.rigidBodies.add(50f, 0f)
        val p = world.particles.add(80f, 0f)
        repeat(30) { world.step(dt) }
        assertEquals(world.particles.yOf(p), world.rigidBodies.yOf(b), 0.01f, "the same gravity, the same fall")
        assertEquals(0.5f * 1500f * 0.25f, world.rigidBodies.yOf(b), 2f, "about g t² / 2 after half a second")
        assertEquals(0f, world.rigidBodies.angleOf(b), "falling doesn't turn it")
    }

    @Test
    fun dampingSlowsSpinningTheWayItSlowsMoving() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO, damping = 1f))
        val bodies = world.rigidBodies
        val b = bodies.add(0f, 0f)
        bodies.setVelocity(b, 10f, 0f)
        bodies.setAngularVelocity(b, 10f)
        repeat(60) { world.step(dt) }
        assertEquals(10f * kotlin.math.exp(-1f), bodies.velocityXOf(b), 0.01f, "a share of e⁻¹ left after a second")
        assertEquals(bodies.velocityXOf(b), bodies.angularVelocityOf(b), 1e-4f)
    }

    @Test
    fun aKinematicBodyFollowsItsPathExactlyAndIgnoresGravity() {
        val world = PhysicsWorld()
        val bodies = world.rigidBodies
        fun pathX(t: Float) = 200f + 80f * cos(t)
        fun pathY(t: Float) = 150f + 80f * sin(t)
        val b = bodies.add(pathX(0f), pathY(0f), kind = BodyKind.KINEMATIC)
        var t = 0f
        repeat(300) {
            t += dt
            bodies.moveKinematic(b, pathX(t), pathY(t), angle = t)
            world.step(dt)
            assertEquals(pathX(t), bodies.xOf(b), 1e-3f, "x at step $it")
            assertEquals(pathY(t), bodies.yOf(b), 1e-3f, "y at step $it")
            assertEquals(t, bodies.angleOf(b), 1e-4f)
        }
        assertEquals(1f, bodies.angularVelocityOf(b), 1e-3f, "turning at the 1 rad/s it was told")
    }

    @Test
    fun aKinematicBodyLeftAloneKeepsItsVelocity() {
        val world = PhysicsWorld()
        val bodies = world.rigidBodies
        val b = bodies.add(0f, 0f, kind = BodyKind.KINEMATIC)
        bodies.setVelocity(b, 30f, 0f)
        repeat(60) { world.step(dt) }
        assertEquals(30f, bodies.xOf(b), 0.01f)
        assertEquals(0f, bodies.yOf(b), "no gravity on a kinematic body")
        assertEquals(0f, bodies.inverseMassOf(b), "nothing pushes it")
    }

    @Test
    fun aStaticBodyNeverMoves() {
        val world = PhysicsWorld()
        val bodies = world.rigidBodies
        val b = bodies.add(10f, 20f, angle = 1f, kind = BodyKind.STATIC)
        bodies.setVelocity(b, 100f, 100f)
        bodies.setAngularVelocity(b, 5f)
        repeat(60) { world.step(dt) }
        assertEquals(10f, bodies.xOf(b))
        assertEquals(20f, bodies.yOf(b))
        assertEquals(1f, bodies.angleOf(b))
        assertEquals(BodyKind.STATIC, bodies.kindOf(b))
    }

    @Test
    fun onlyAKinematicBodyCanBeToldWhereToGo() {
        val bodies = PhysicsWorld().rigidBodies
        val b = bodies.add(0f, 0f)
        assertFailsWith<IllegalArgumentException> { bodies.moveKinematic(b, 1f, 1f) }
    }

    @Test
    fun aFastBodyMovesNoFurtherThanMaxStepASubstep() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO, damping = 0f, maxStep = 4f))
        val b = world.rigidBodies.add(0f, 0f)
        world.rigidBodies.setVelocity(b, 100_000f, 0f)
        world.step(dt)
        assertEquals(16f, world.rigidBodies.xOf(b), 1e-3f, "four substeps of at most 4 units")
    }

    @Test
    fun theVelocityAtAPointAddsTheTurning() {
        val bodies = space().rigidBodies
        val b = bodies.add(0f, 0f)
        bodies.setAngularVelocity(b, 2f)
        // y grows down, so turning clockwise on screen moves a point to the right of the centre down
        assertEquals(0f, bodies.pointVelocityXOf(b, 10f, 0f))
        assertEquals(20f, bodies.pointVelocityYOf(b, 10f, 0f))
        assertEquals(-20f, bodies.pointVelocityXOf(b, 0f, 10f))
    }

    @Test
    fun aRemovedBodysHandleIsReused() {
        val bodies = PhysicsWorld().rigidBodies
        val a = bodies.add(0f, 0f)
        val b = bodies.add(1f, 1f)
        bodies.remove(a)
        assertFalse(bodies.isAlive(a))
        assertEquals(1, bodies.size)
        assertEquals(a, bodies.add(2f, 2f))
        assertTrue(bodies.isAlive(b))
    }

    @Test
    fun aBodyNeedsAMassAndInertiaAndAPlace() {
        val bodies = PhysicsWorld().rigidBodies
        assertFailsWith<IllegalArgumentException> { bodies.add(0f, 0f, mass = 0f) }
        assertFailsWith<IllegalArgumentException> { bodies.add(0f, 0f, inertia = -1f) }
        assertFailsWith<IllegalArgumentException> { bodies.add(Float.NaN, 0f) }
        assertFailsWith<IllegalArgumentException> { bodies.add(0f, 0f, angle = Float.POSITIVE_INFINITY) }
    }

    @Test
    fun onlyAnAwakeDynamicBodyIsAwakeDynamicAndAnySleepingOrNotIsDynamic() {
        val world = space()
        val bodies = world.rigidBodies
        val falling = world.rigidBody(Shape.box(10f, 10f), 0f, 0f)
        val sleeping = world.rigidBody(Shape.box(10f, 10f), 50f, 0f)
        val wall = world.rigidBody(Shape.box(10f, 10f), 100f, 0f, kind = BodyKind.STATIC)
        val lift = world.rigidBody(Shape.box(10f, 10f), 150f, 0f, kind = BodyKind.KINEMATIC)
        bodies.sleep(sleeping.index, group = 0)
        assertTrue(bodies.isAwakeDynamic(falling.index))
        assertFalse(bodies.isAwakeDynamic(sleeping.index))
        assertTrue(bodies.isDynamic(sleeping.index))
        for (other in listOf(wall, lift)) {
            assertFalse(bodies.isDynamic(other.index))
            assertFalse(bodies.isAwakeDynamic(other.index))
        }
        bodies.remove(falling)
        assertFalse(bodies.isDynamic(falling.index))
    }

    @Test
    fun removingABodyDropsItsJointsSoAReusedSlotDoesNotInheritThem() {
        val world = space()
        val bodies = world.rigidBodies
        val crate = world.rigidBody(Shape.box(40f, 40f), 100f, 100f)
        val wheel = world.rigidBody(Shape.circle(15f), 110f, 100f)
        val hinge = world.joints.hinge(crate, wheel, 110f, 100f)
        val keep = world.rigidBody(Shape.box(20f, 20f), 400f, 100f)
        val other = world.joints.hinge(keep, JointEnd.WORLD, 400f, 100f)
        bodies.remove(wheel)
        assertFalse(world.joints.isAlive(hinge))
        assertTrue(world.joints.isAlive(other))
        // The next body takes wheel's slot, is not pulled to the old anchor, and collides with the crate.
        val newcomer = world.rigidBody(Shape.circle(15f), 110f, 100f)
        assertEquals(wheel, newcomer)
        repeat(30) { world.step(dt) }
        val dx = bodies.xOf(newcomer) - bodies.xOf(crate)
        val dy = bodies.yOf(newcomer) - bodies.yOf(crate)
        assertTrue(dx * dx + dy * dy > 25f * 25f, "new body is pushed out of the crate instead of staying hinged: ($dx, $dy)")
    }

    @Test
    fun removingABodyDropsItsAttachmentsSoAReusedSlotDoesNotInheritThem() {
        val world = space()
        val bodies = world.rigidBodies
        val crate = world.rigidBody(Shape.box(20f, 20f), 100f, 100f)
        val other = world.rigidBody(Shape.box(20f, 20f), 300f, 100f)
        val p = world.particles.add(100f, 100f, mass = Particles.STATIC)
        val q = world.particles.add(300f, 100f, mass = Particles.STATIC)
        val tie = world.attach(p, crate)
        val kept = world.attach(q, other)
        bodies.remove(crate)
        assertFalse(world.attachments.isAlive(tie))
        assertTrue(world.attachments.isAlive(kept))
        val newcomer = world.rigidBody(Shape.box(20f, 20f), 200f, 100f)
        assertEquals(crate, newcomer)
        repeat(10) { world.step(dt) }
        assertEquals(200f, bodies.xOf(newcomer), 0.01f, "new body is not pulled to the old attachment")
    }

    @Test
    fun clearingBodiesDropsJointsAttachmentsAndGrab() {
        val world = space()
        val bodies = world.rigidBodies
        val a = world.rigidBody(Shape.box(40f, 40f), 100f, 100f)
        val b = world.rigidBody(Shape.circle(15f), 110f, 100f)
        val hinge = world.joints.hinge(a, b, 110f, 100f)
        val p = world.particles.add(100f, 100f, mass = Particles.STATIC)
        val tie = world.attach(p, a)
        world.grab(100f, 100f)

        bodies.clear()

        assertEquals(0, world.joints.size)
        assertFalse(world.joints.isAlive(hinge))
        assertEquals(0, world.attachments.size)
        assertFalse(world.attachments.isAlive(tie))
        assertEquals(null, world.hand.bodyOrNull)

        val nextA = world.rigidBody(Shape.box(40f, 40f), 300f, 300f)
        val nextB = world.rigidBody(Shape.circle(15f), 310f, 300f)
        repeat(30) { world.step(dt) }
        val dx = bodies.xOf(nextB) - bodies.xOf(nextA)
        val dy = bodies.yOf(nextB) - bodies.yOf(nextA)
        assertTrue(dx * dx + dy * dy > 25f * 25f, "new bodies collide instead of inheriting the old joint")
    }
}
