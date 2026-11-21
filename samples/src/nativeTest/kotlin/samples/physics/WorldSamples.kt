package samples.physics

import klyph.math.Vec2
import klyph.physics.Material
import klyph.physics.PhysicsConfig
import klyph.physics.PhysicsWorld
import klyph.physics.Shape
import klyph.physics.rigidBody
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class WorldSamples {
    @Sample
    fun step() {
        val world = PhysicsWorld()
        val ball = world.particles.add(x = 0f, y = 0f)

        // A second of 60 fixed steps; gravity pulls down, and y grows down.
        repeat(60) { world.step(1.seconds / 60) }

        assertPrints(world.stepCount, "60")
        assertTrue(world.particles.yOf(ball) > 500f)
        assertPrints(world.particles.xOf(ball), "0.0")
    }

    @Sample
    fun physicsConfig() {
        // Moon gravity, stiffer solving, and rigid bodies that never fall asleep.
        val config = PhysicsConfig(
            gravity = Vec2(0f, 250f),
            substeps = 8,
            timeToSleep = Duration.INFINITE,
        )
        val world = PhysicsWorld(config)
        assertPrints(world.config.substeps, "8")
        assertPrints(config == PhysicsConfig(gravity = Vec2(0f, 250f), substeps = 8, timeToSleep = Duration.INFINITE), "true")
    }

    @Sample
    fun material() {
        val bouncy = Material(friction = 0.2f, restitution = 0.8f)
        val world = PhysicsWorld()
        val ball = world.rigidBody(Shape.circle(8f), x = 0f, y = 0f, material = bouncy)
        world.rigidBodies.setMaterial(ball, Material.ICE)
        assertPrints(bouncy, "Material(friction=0.2, staticFriction=0.6, restitution=0.8)")
    }

    @Sample
    fun grab() {
        val world = PhysicsWorld()
        val crate = world.rigidBody(Shape.box(20f, 20f), x = 100f, y = 100f)

        // Take the crate by its corner and drag it to the right.
        world.grab(108f, 92f)
        assertPrints(world.hand.bodyOrNull == crate, "true")
        repeat(30) {
            world.moveGrab(200f, 92f)
            world.step(1.seconds / 60)
        }
        assertTrue(world.rigidBodies.xOf(crate) > 150f)

        world.release()
        assertPrints(world.hand.isHolding, "false")
    }
}
