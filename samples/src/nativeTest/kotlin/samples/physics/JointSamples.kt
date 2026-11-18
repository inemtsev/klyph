package samples.physics

import klyph.physics.BodyKind
import klyph.physics.JointEnd
import klyph.physics.PhysicsWorld
import klyph.physics.Shape
import klyph.physics.rope
import samples.Sample
import samples.assertPrints
import kotlin.math.PI
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class JointSamples {
    @Sample
    fun hinge() {
        val world = PhysicsWorld()
        val door = world.rigidBody(Shape.box(40f, 4f), x = 20f, y = 0f)
        // Hinged to the world at its left end: it swings down about that point.
        val hinge = world.joints.hinge(door, JointEnd.WORLD, x = 0f, y = 0f)
        repeat(60) { world.step(1.seconds / 60) }
        assertTrue(world.rigidBodies.angleOf(door) > 0.5f)
        assertPrints(world.joints.kindOf(hinge), "HINGE")
    }

    @Sample
    fun setLimits() {
        val world = PhysicsWorld()
        val door = world.rigidBody(Shape.box(40f, 4f), x = 20f, y = 0f)
        val hinge = world.joints.hinge(door, JointEnd.WORLD, x = 0f, y = 0f)
        // It may turn at most a quarter turn down.
        world.joints.setLimits(hinge, lower = 0f, upper = (PI / 4).toFloat())
        repeat(120) { world.step(1.seconds / 60) }
        assertTrue(world.joints.angleOf(hinge) <= (PI / 4).toFloat() + 0.02f)
    }

    @Sample
    fun setMotor() {
        val world = PhysicsWorld()
        val wheel = world.rigidBody(Shape.circle(10f), x = 0f, y = 0f)
        val axle = world.joints.hinge(wheel, JointEnd.WORLD, x = 0f, y = 0f)
        // Drives the world round the wheel at half a turn a second, so the wheel turns the other way.
        world.joints.setMotor(axle, speed = PI.toFloat(), maxTorque = 1_000_000f)
        repeat(60) { world.step(1.seconds / 60) }
        assertPrints(world.rigidBodies.angularVelocityOf(wheel), "-3.1415927")
    }

    @Sample
    fun slider() {
        val world = PhysicsWorld()
        val bead = world.rigidBody(Shape.circle(4f), x = 0f, y = 0f)
        // Slides along a 45-degree track through where it is now.
        world.joints.slider(bead, JointEnd.WORLD, x = 0f, y = 0f, axisX = 1f, axisY = 1f)
        repeat(30) { world.step(1.seconds / 60) }
        val x = world.rigidBodies.xOf(bead)
        val y = world.rigidBodies.yOf(bead)
        assertTrue(y > 10f && kotlin.math.abs(x - y) < 0.5f)
    }

    @Sample
    fun weld() {
        val world = PhysicsWorld()
        world.rigidBody(Shape.box(200f, 10f), x = 0f, y = 100f, kind = BodyKind.STATIC)
        val left = world.rigidBody(Shape.box(20f, 20f), x = -10f, y = 0f)
        val right = world.rigidBody(Shape.box(20f, 20f), x = 10f, y = 0f)
        // The two crates are one from now on, unless pulled harder than breakForce.
        world.joints.weld(left, right, x = 0f, y = 0f, breakForce = 1e9f)
        repeat(60) { world.step(1.seconds / 60) }
        val gap = world.rigidBodies.xOf(right) - world.rigidBodies.xOf(left)
        assertTrue(gap in 19.9f..20.1f)
    }

    @Sample
    fun distance() {
        val world = PhysicsWorld()
        val bob = world.rigidBody(Shape.circle(5f), x = 30f, y = 0f)
        // A stick from the bob's middle to the world's origin, 30 long, as they are now.
        world.joints.distance(bob, JointEnd.WORLD, ax = 30f, ay = 0f, bx = 0f, by = 0f)
        repeat(60) { world.step(1.seconds / 60) }
        val x = world.rigidBodies.xOf(bob)
        val y = world.rigidBodies.yOf(bob)
        assertTrue(kotlin.math.sqrt(x * x + y * y) in 29.5f..30.5f)
    }
}

class AttachmentSamples {
    @Sample
    fun attach() {
        val world = PhysicsWorld()
        val crate = world.rigidBody(Shape.box(20f, 20f), x = 100f, y = 0f)
        val rope = world.rope(fromX = 0f, fromY = 0f, toX = 90f, toY = 0f, segments = 9)
        // Ties the rope's free end to the crate's left face: the crate swings on the rope.
        val knot = world.attach(rope.last, crate)
        repeat(60) { world.step(1.seconds / 60) }
        assertPrints(world.attachments.bodyOf(knot) == crate, "true")
        assertTrue(world.rigidBodies.yOf(crate) > 20f)
    }
}
