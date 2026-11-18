package samples.physics

import klyph.collision.CollisionLayers
import klyph.physics.Motion
import klyph.physics.ParticleCollision
import klyph.physics.PhysicsWorld
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ParticleSamples {
    @Sample
    fun add() {
        val world = PhysicsWorld()
        val spark = world.particles.add(x = 10f, y = 20f)
        // A grain of sand: round, and pushed by everything else that collides.
        val grain = world.particles.add(x = 30f, y = 20f, mass = 0.5f, radius = 2f, collision = ParticleCollision.EVERYTHING)

        assertPrints(world.particles.size, "2")
        assertPrints(world.particles.xOf(spark), "10.0")
        assertPrints(world.particles.inverseMassOf(grain), "2.0")
        assertPrints(world.particles.collisionOf(grain), "EVERYTHING")
    }

    @Sample
    fun forEach() {
        val world = PhysicsWorld()
        repeat(3) { world.particles.add(x = it * 10f, y = 0f) }
        var sum = 0f
        world.particles.forEach { p -> sum += world.particles.xOf(p) }
        assertPrints(sum, "30.0")
    }

    @Sample
    fun moveTo() {
        val world = PhysicsWorld()
        val p = world.particles.add(x = 0f, y = 0f)
        // Teleports it, at rest: it does not fly off at the speed of the jump.
        world.particles.moveTo(p, 500f, 0f)
        world.step(1.seconds / 60)
        assertTrue(world.particles.xOf(p) == 500f)
    }

    @Sample
    fun setMotion() {
        val world = PhysicsWorld()
        val bullet = world.particles.add(x = 0f, y = 0f, radius = 1f)
        world.particles.setMotion(bullet, Motion.BULLET)
        world.setVelocity(bullet, 5_000f, 0f)
        assertPrints(world.particles.motionOf(bullet), "BULLET")
    }

    @Sample
    fun setLayers() {
        val world = PhysicsWorld()
        val player = CollisionLayers.of(0)
        val enemy = CollisionLayers.of(1)
        val grain = world.particles.add(x = 0f, y = 0f, radius = 2f, collision = ParticleCollision.EVERYTHING)
        // On the enemy layer, colliding with the player only.
        world.particles.setLayers(grain, layers = enemy, mask = player)
        assertPrints(world.particles.layersOf(grain) == enemy, "true")
        assertPrints(world.particles.maskOf(grain) == player, "true")
    }
}
