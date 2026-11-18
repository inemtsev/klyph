package samples.physics

import klyph.entity.Transform
import klyph.entity.World
import klyph.physics.Physics
import klyph.physics.PhysicsBody
import klyph.physics.PhysicsWorld
import klyph.physics.Shape
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AppSamples {
    @Sample
    fun physics() {
        // In an app, App.addPhysics makes the Physics and steps it every fixed step.
        val physics = Physics(PhysicsWorld())
        val entities = World()
        val crate = physics.world.rigidBody(Shape.box(20f, 20f), x = 100f, y = 0f)
        val entity = entities.create()
        entities.add(entity, Transform, Transform())
        entities.add(entity, PhysicsBody, PhysicsBody.of(crate))

        repeat(30) { physics.step(entities, 1.seconds / 60) }

        // The entity's Transform is where the crate fell to.
        val transform = entities.get(entity, Transform)
        assertPrints(transform.x, "100.0")
        assertTrue(transform.y > 50f)
        assertPrints(physics.stepCount, "30")
    }

    @Sample
    fun physicsBody() {
        val world = PhysicsWorld()
        val crate = world.rigidBody(Shape.box(20f, 20f), x = 0f, y = 0f)
        val drop = world.particles.add(x = 0f, y = 0f)
        // One link type for rigid bodies, soft bodies and particles.
        assertPrints(PhysicsBody.of(crate), "PhysicsBody(RigidBody(0))")
        assertPrints(PhysicsBody.of(drop), "PhysicsBody(Particle(0))")
    }
}
