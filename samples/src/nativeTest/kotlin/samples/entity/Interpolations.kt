package samples.entity

import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.entity.World
import samples.Sample
import kotlin.test.assertTrue

class Interpolations {
    @Sample
    fun interpolated() {
        val world = World()
        val ship = world.create()
        world.add(ship, Transform, Transform(x = 10f, y = 20f))
        // The marker is its own component value.
        world.add(ship, Interpolated, Interpolated)
        assertTrue(world.has(ship, Interpolated))
    }
}
