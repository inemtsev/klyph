package samples.physics

import klyph.collision.CollisionLayers
import klyph.physics.BodyCollision
import klyph.physics.Motion
import klyph.physics.PhysicsWorld
import klyph.physics.SolidCollision
import klyph.physics.SolidGrid
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
        val grain = world.particles.add(x = 30f, y = 20f, mass = 0.5f, radius = 2f, bodyCollision = BodyCollision.COLLIDE)

        assertPrints(world.particles.size, "2")
        assertPrints(world.particles.xOf(spark), "10.0")
        assertPrints(world.particles.inverseMassOf(grain), "2.0")
        assertPrints(world.particles.bodyCollisionOf(grain), "COLLIDE")
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
        val grain = world.particles.add(x = 0f, y = 0f, radius = 2f, bodyCollision = BodyCollision.COLLIDE)
        // On the enemy layer, colliding with the player only.
        world.particles.setLayers(grain, layers = enemy, mask = player)
        assertPrints(world.particles.layersOf(grain) == enemy, "true")
        assertPrints(world.particles.maskOf(grain) == player, "true")
    }

    @Sample
    fun setSolidCollision() {
        val world = PhysicsWorld()
        // A floor of 16-unit tiles along row 9, its top at y = 144.
        val grid = SolidGrid(columns = 20, rows = 10, tileSize = 16f)
        for (column in 0 until 20) grid.fill(column, 9)
        world.solids = grid
        val drop = world.particles.add(x = 160f, y = 100f, radius = 2f)
        world.particles.setSolidCollision(drop, SolidCollision.PASS)
        assertPrints(world.particles.solidCollisionOf(drop), "PASS")

        repeat(60) { world.step(1.seconds / 60) }

        // It fell through the floor.
        assertTrue(world.particles.yOf(drop) > 160f)
    }
}
