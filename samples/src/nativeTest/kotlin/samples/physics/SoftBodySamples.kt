package samples.physics

import klyph.physics.PhysicsWorld
import klyph.physics.SelfCollision
import klyph.physics.SoftBodyShape
import klyph.physics.SoftBodyTuning
import klyph.physics.SolidGrid
import klyph.physics.SolidTags
import klyph.physics.centerXOf
import klyph.physics.maxYOf
import klyph.physics.softBody
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SoftBodySamples {
    @Sample
    fun semiDisc() {
        // A slime: a dome 13 units round, its ring of 22 points, its core 34 times a ring point's mass.
        val slime = SoftBodyShape.semiDisc(radius = 13f, arcSegments = 14, baseSegments = 8, coreMass = 34f, heightScale = 1.3f)
        assertPrints(slime, "SoftBodyShape(ring=22, coreMass=34.0)")
        val ball = SoftBodyShape.circle(radius = 10f, segments = 16, coreMass = 4f)
        assertPrints(ball.ringCount, "16")
    }

    @Sample
    fun add() {
        val world = PhysicsWorld()
        // A floor of 16-unit tiles along row 9, its top at y = 144.
        val grid = SolidGrid(columns = 20, rows = 10, tileSize = 16f)
        for (column in 0 until 20) grid.fill(column, 9)
        world.solids = grid
        val slime = world.softBody(SoftBodyShape.semiDisc(13f, 14, 8, coreMass = 34f), centerX = 160f, baseY = 100f)

        repeat(120) { world.step(1.seconds / 60) }

        // It fell onto the floor and sits on it, its bottom at the floor's top.
        assertTrue(world.softBodies.maxYOf(slime) in 140f..146f)
        assertPrints(world.softBodies.isOnFloor(slime), "true")
        assertPrints(world.softBodies.particlesOf(slime).size, "23")
    }

    @Sample
    fun softBodyTuning() {
        // Softer spokes and area than the default: it squashes and sags more.
        val jelly = SoftBodyTuning(spokeCompliance = 0.002f, areaCompliance = 0.002f)
        val world = PhysicsWorld()
        // A floor of 16-unit tiles along row 9, its top at y = 144.
        val grid = SolidGrid(columns = 20, rows = 10, tileSize = 16f)
        for (column in 0 until 20) grid.fill(column, 9)
        world.solids = grid
        val blob = world.softBody(SoftBodyShape.circle(12f, 16, coreMass = 4f), centerX = 160f, baseY = 140f, tuning = jelly)
        assertPrints(world.softBodies.tuningOf(blob) == jelly, "true")
    }

    @Sample
    fun placeAt() {
        val world = PhysicsWorld()
        // A floor of 16-unit tiles along row 9, its top at y = 144.
        val grid = SolidGrid(columns = 20, rows = 10, tileSize = 16f)
        for (column in 0 until 20) grid.fill(column, 9)
        world.solids = grid
        val blob = world.softBody(SoftBodyShape.circle(12f, 16, coreMass = 4f), centerX = 40f, baseY = 140f)
        world.softBodies.placeAt(blob, centerX = 200f, baseY = 140f)
        assertTrue(world.softBodies.centerXOf(blob) in 199f..201f)
    }

    @Sample
    fun addVelocity() {
        val world = PhysicsWorld()
        // A floor of 16-unit tiles along row 9, its top at y = 144.
        val grid = SolidGrid(columns = 20, rows = 10, tileSize = 16f)
        for (column in 0 until 20) grid.fill(column, 9)
        world.solids = grid
        val blob = world.softBody(SoftBodyShape.circle(12f, 16, coreMass = 4f), centerX = 40f, baseY = 140f)
        world.softBodies.addVelocity(blob, vx = 300f, vy = -400f)
        world.step(1.seconds / 60)
        assertTrue(world.softBodies.meanVelocityXOf(blob) > 200f)
    }

    @Sample
    fun setSelfCollision() {
        val world = PhysicsWorld()
        // A floor of 16-unit tiles along row 9, its top at y = 144.
        val grid = SolidGrid(columns = 20, rows = 10, tileSize = 16f)
        for (column in 0 until 20) grid.fill(column, 9)
        world.solids = grid
        val sheet = world.softBody(SoftBodyShape.box(80f, 8f, across = 10, down = 1, coreMass = 1f), centerX = 160f, baseY = 100f)
        // A long thin body folds over when it lands; keep its skin from passing through itself.
        world.softBodies.setSelfCollision(sheet, SelfCollision.COLLIDE)
        assertPrints(world.softBodies.selfCollisionOf(sheet), "COLLIDE")
    }

    @Sample
    fun floorTagsOf() {
        val ice = SolidTags.of(0)
        val world = PhysicsWorld()
        val grid = SolidGrid(columns = 20, rows = 10, tileSize = 16f)
        for (column in 0 until 20) grid.fill(column, 9, tags = ice)
        world.solids = grid
        val blob = world.softBody(SoftBodyShape.circle(12f, 16, coreMass = 4f), centerX = 160f, baseY = 140f)
        repeat(30) { world.step(1.seconds / 60) }
        assertPrints(world.softBodies.floorTagsOf(blob).intersects(ice), "true")
    }
}
