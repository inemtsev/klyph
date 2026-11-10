package samples.entity

import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.entity.World
import samples.Sample
import samples.assertPrints

class Velocities {
    @Sample
    fun velocity() {
        val world = World()
        val popup = world.create()
        world.add(popup, Transform, Transform(x = 100f, y = 200f))
        // In an app, the engine moves the popup 40 units up every second, one fixed step at a time.
        world.add(popup, Velocity, Velocity(dy = -40f))
        // A system can steer it by changing the velocity.
        world.get(popup, Velocity).dx = 10f
        assertPrints(world.get(popup, Velocity), "Velocity(dx=10.0, dy=-40.0)")
    }
}
