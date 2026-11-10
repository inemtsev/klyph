package samples.collision

import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.collision.SweepHit
import klyph.entity.Transform
import klyph.entity.World
import klyph.event.Events
import samples.Sample
import samples.assertPrints

class Sweeps {
    @Sample
    fun sweepBox() {
        val world = World()
        val wall = world.create()
        world.add(wall, Transform, Transform(x = 105f, y = 0f))
        world.add(wall, Collider, Collider(Shape.Box(width = 10f, height = 200f)))
        world.add(wall, Solid, Solid())
        // In a game the engine keeps app.collisions up to date with the world.
        val collisions = Collisions()
        collisions.detect(world, Events())

        // A 20 by 20 box moving 100 to the right reaches the wall's left face, at x = 100, after 90.
        val hit = collisions.sweepBox(x = 0f, y = 0f, width = 20f, height = 20f, dx = 100f, dy = 0f)
        assertPrints(hit, "SweepHit(entity=Entity(0:0), fraction=0.9, normalX=-1.0, normalY=0.0)")
        // Moving away from it, or with no move at all, nothing is in the way.
        assertPrints(collisions.sweepBox(x = 0f, y = 0f, width = 20f, height = 20f, dx = -100f, dy = 0f), "null")
        assertPrints(collisions.sweepBox(x = 0f, y = 0f, width = 20f, height = 20f, dx = 0f, dy = 0f), "null")
    }

    @Sample
    fun sweepBoxTo() {
        val world = World()
        for (x in listOf(300f, 100f)) {
            val post = world.create()
            world.add(post, Transform, Transform(x = x, y = 0f))
            world.add(post, Collider, Collider(Shape.Box(width = 20f, height = 20f)))
            world.add(post, Solid, Solid())
        }
        // In a game the engine keeps app.collisions up to date with the world.
        val collisions = Collisions()
        collisions.detect(world, Events())

        // Reuse one list to avoid allocating per query.
        val hits = ArrayList<SweepHit>()
        collisions.sweepBoxTo(hits, x = 0f, y = 0f, width = 20f, height = 20f, dx = 400f, dy = 0f)
        assertPrints(hits.map { it.entity }, "[Entity(1:0), Entity(0:0)]")
        assertPrints(hits.map { it.fraction }, "[0.2, 0.7]")
    }

    @Sample
    fun sweepCollider() {
        val world = World()
        val wall = world.create()
        world.add(wall, Transform, Transform(x = 105f, y = 0f))
        world.add(wall, Collider, Collider(Shape.Box(width = 10f, height = 200f)))
        world.add(wall, Solid, Solid())
        val hero = world.create()
        world.add(hero, Transform, Transform(x = 0f, y = 0f))
        // The box is 20 wide and sits 10 to the right of the hero's position, from 0 to 20.
        world.add(hero, Collider, Collider(Shape.Box(width = 20f, height = 40f), offsetX = 10f))
        // In a game the engine keeps app.collisions up to date with the world.
        val collisions = Collisions()
        collisions.detect(world, Events())

        // Rolled 100 to the right, the box's right edge reaches the wall at 100 after 80.
        val hit = collisions.sweepCollider(hero, dx = 100f, dy = 0f)
        assertPrints(hit, "SweepHit(entity=Entity(0:0), fraction=0.8, normalX=-1.0, normalY=0.0)")
    }

    @Sample
    fun floorBelowOrNull() {
        val world = World()
        // A roof from x = 0 to 100 whose top is at y = 100.
        val roof = world.create()
        world.add(roof, Transform, Transform(x = 50f, y = 120f))
        world.add(roof, Collider, Collider(Shape.Box(width = 100f, height = 40f)))
        world.add(roof, Solid, Solid())
        // In a game the engine keeps app.collisions up to date with the world.
        val collisions = Collisions()
        collisions.detect(world, Events())

        // Feet at y = 98: the roof is 2 below them, a quarter of the way down a probe 8 deep.
        val hit = collisions.floorBelowOrNull(x = 80f, y = 98f, width = 4f, depth = 8f)
        assertPrints(hit, "SweepHit(entity=Entity(0:0), fraction=0.25, normalX=0.0, normalY=-1.0)")
        // Past the end of the roof there is nothing to stand on.
        assertPrints(collisions.floorBelowOrNull(x = 120f, y = 98f, width = 4f, depth = 8f), "null")
    }
}
