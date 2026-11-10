package samples.collision

import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.Passable
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.entity.Transform
import klyph.entity.World
import klyph.event.Events
import samples.Sample
import samples.assertPrints

class Solids {
    @Sample
    fun solid() {
        val world = World()
        // A rooftop 400 units wide whose top edge is at y = 300.
        val roof = world.create()
        world.add(roof, Transform, Transform(x = 200f, y = 400f))
        world.add(roof, Collider, Collider(Shape.Box(width = 400f, height = 200f)))
        world.add(roof, Solid, Solid())
        assertPrints(world.get(roof, Solid), "Solid(passable=NEVER)")

        // A 20 by 20 box dropped from y = 200 lands with its bottom edge at the roof's top.
        // In a game the engine keeps app.collisions up to date with the world.
        val collisions = Collisions()
        collisions.detect(world, Events())
        val hit = collisions.sweepBox(x = 100f, y = 200f, width = 20f, height = 20f, dx = 0f, dy = 200f)
        assertPrints(hit, "SweepHit(entity=Entity(0:0), fraction=0.45, normalX=0.0, normalY=-1.0)")
    }

    @Sample
    fun oneWay() {
        val world = World()
        // A thin ledge with its top at y = 100 that bodies jump up through.
        val ledge = world.create()
        world.add(ledge, Transform, Transform(x = 100f, y = 105f))
        world.add(ledge, Collider, Collider(Shape.Box(width = 200f, height = 10f)))
        world.add(ledge, Solid, Solid(passable = Passable.FROM_BELOW))
        // In a game the engine keeps app.collisions up to date with the world.
        val collisions = Collisions()
        collisions.detect(world, Events())

        // Coming down from above, a box lands on it.
        assertPrints(collisions.sweepBox(x = 100f, y = 50f, width = 20f, height = 20f, dx = 0f, dy = 100f)?.normalY, "-1.0")
        // Going up from below, it passes through.
        assertPrints(collisions.sweepBox(x = 100f, y = 150f, width = 20f, height = 20f, dx = 0f, dy = -100f), "null")
    }
}
