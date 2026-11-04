package samples.entity

import klyph.entity.Lifetime
import klyph.entity.Transform
import klyph.entity.World
import samples.Sample
import samples.assertPrints
import kotlin.time.Duration.Companion.seconds

class Lifetimes {
    @Sample
    fun lifetime() {
        val world = World()
        val bullet = world.create()
        world.add(bullet, Transform, Transform(x = 10f, y = 20f))
        // In an app, the engine destroys the bullet two seconds from now.
        world.add(bullet, Lifetime, Lifetime(2.seconds))
        assertPrints(world.get(bullet, Lifetime), "Lifetime(remaining=2s)")
    }
}
