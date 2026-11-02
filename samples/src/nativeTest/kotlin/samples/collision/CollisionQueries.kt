package samples.collision

import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Collisions
import klyph.collision.Overlap
import klyph.collision.Shape
import klyph.collision.overlaps
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.event.Events
import samples.Sample
import samples.assertPrints

class CollisionQueries {
    @Sample
    fun detect() {
        val world = World()
        val ship = world.create()
        world.add(ship, Transform, Transform(x = 0f, y = 0f))
        world.add(ship, Collider, Collider(Shape.Box(width = 32f, height = 16f)))
        val rock = world.create()
        world.add(rock, Transform, Transform(x = 20f, y = 0f))
        world.add(rock, Collider, Collider(Shape.Circle(8f)))

        // In a game the engine does this for app.collisions once per frame.
        val collisions = Collisions(cellSize = 64f)
        val events = Events()
        collisions.detect(world, events)

        val hits = ArrayList<Overlap>()
        events.forEach<Overlap> { hits.add(it) }
        assertPrints(hits, "[Overlap(a=Entity(0:0), b=Entity(1:0))]")
        assertPrints(collisions.pairCount, "1")
    }

    @Sample
    fun overlaps() {
        val enemy = CollisionLayers.of(1)
        val world = World()
        val near = world.create()
        world.add(near, Transform, Transform(x = 10f, y = 0f))
        world.add(near, Collider, Collider(Shape.Circle(8f), layers = enemy))
        val far = world.create()
        world.add(far, Transform, Transform(x = 500f, y = 0f))
        world.add(far, Collider, Collider(Shape.Circle(8f), layers = enemy))
        val collisions = Collisions()
        collisions.detect(world, Events())

        assertPrints(collisions.overlaps(Shape.Circle(50f), x = 0f, y = 0f, mask = enemy), "[Entity(0:0)]")
        assertPrints(collisions.overlaps(Shape.Box(width = 2000f, height = 10f), x = 0f, y = 0f), "[Entity(0:0), Entity(1:0)]")
    }

    @Sample
    fun overlapsTo() {
        val world = World()
        repeat(3) { i ->
            val crate = world.create()
            world.add(crate, Transform, Transform(x = i * 20f, y = 0f))
            world.add(crate, Collider, Collider(Shape.Box(width = 16f, height = 16f)))
        }
        val collisions = Collisions()
        collisions.detect(world, Events())

        // Reuse one list across queries to avoid allocating per query.
        val hits = ArrayList<Entity>()
        collisions.overlapsTo(hits, Shape.Circle(6f), x = 10f, y = 0f)
        assertPrints(hits, "[Entity(0:0), Entity(1:0)]")
        hits.clear()
        collisions.overlapsTo(hits, Shape.Circle(2f), x = 40f, y = 0f)
        assertPrints(hits, "[Entity(2:0)]")
    }

    @Sample
    fun firstOverlapOrNull() {
        val world = World()
        val wall = world.create()
        world.add(wall, Transform, Transform(x = 100f, y = 0f))
        world.add(wall, Collider, Collider(Shape.Box(width = 10f, height = 200f)))
        val collisions = Collisions()
        collisions.detect(world, Events())

        assertPrints(collisions.firstOverlapOrNull(Shape.Circle(4f), x = 98f, y = 50f), "Entity(0:0)")
        assertPrints(collisions.firstOverlapOrNull(Shape.Circle(4f), x = 50f, y = 50f), "null")
    }
}
