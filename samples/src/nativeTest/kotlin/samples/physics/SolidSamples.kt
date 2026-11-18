package samples.physics

import klyph.physics.PhysicsWorld
import klyph.physics.Solid
import klyph.physics.SolidGrid
import klyph.physics.SolidShape
import klyph.physics.SolidTags
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SolidSamples {
    @Sample
    fun solidGrid() {
        // A level 40 tiles across and 20 down, each 16 units: a floor, and a slope up to a ledge.
        val grid = SolidGrid(columns = 40, rows = 20, tileSize = 16f)
        for (column in 0 until 40) grid.fill(column, 19)
        grid.fill(10, 18, shape = SolidShape.RISING)
        grid.fill(11, 18)
        val world = PhysicsWorld()
        world.solids = grid
        assertPrints(grid.isSolidAt(8f, 310f), "true")
        assertPrints(grid.atOrNull(11, 18)?.shape, "BOX")
        assertPrints(grid.atOrNull(12, 18), "null")
    }

    @Sample
    fun solidTags() {
        val ice = SolidTags.of(0)
        val lava = SolidTags.of(1)
        val both = ice + lava
        assertPrints(both, "SolidTags(0, 1)")
        assertPrints(both.intersects(lava), "true")
        assertPrints(ice.intersects(lava), "false")
    }

    @Sample
    fun moving() {
        val grid = SolidGrid(columns = 20, rows = 20, tileSize = 16f)
        // A platform that is not on the grid; move it by changing its top and bottom.
        val platform = Solid.moving(left = 32f, top = 200f, right = 96f, bottom = 208f)
        grid.add(platform)
        val world = PhysicsWorld()
        world.solids = grid
        val drop = world.particles.add(x = 64f, y = 150f)
        repeat(60) { world.step(1.seconds / 60) }
        assertTrue(world.particles.yOf(drop) in 199f..200.5f)
        assertPrints(grid.moving.size, "1")
    }

    @Sample
    fun contains() {
        val grid = SolidGrid(columns = 4, rows = 4, tileSize = 16f)
        val slope = grid.fill(1, 1, shape = SolidShape.RISING)
        // A rising slope fills the tile's bottom-right half.
        assertPrints(slope.contains(30f, 30f), "true")
        assertPrints(slope.contains(18f, 18f), "false")
    }
}
