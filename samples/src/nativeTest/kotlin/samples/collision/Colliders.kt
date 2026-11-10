package samples.collision

import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Collisions
import klyph.collision.Shape
import klyph.entity.Transform
import klyph.entity.World
import klyph.event.Events
import samples.Sample
import samples.assertPrints

class Colliders {
    @Sample
    fun collider() {
        val enemy = CollisionLayers.of(1)
        val bullet = CollisionLayers.of(2)
        val world = World()
        val shot = world.create()
        world.add(shot, Transform, Transform(x = 100f, y = 40f))
        world.add(shot, Collider, Collider(Shape.Circle(4f), layers = bullet, mask = enemy))

        assertPrints(
            world.get(shot, Collider),
            "Collider(shape=Circle(radius=4.0), offset=0.0x0.0, layers=CollisionLayers(2), mask=CollisionLayers(1))",
        )
    }

    @Sample
    fun scalesWithTransform() {
        val world = World()
        // A ring spun by narrowing its scale, with a hitbox that keeps its full size.
        val ring = world.create()
        world.add(ring, Transform, Transform(x = 0f, y = 0f, scaleX = 0.2f))
        world.add(ring, Collider, Collider(Shape.Circle(12f)).apply { scalesWithTransform = false })
        val hero = world.create()
        world.add(hero, Transform, Transform(x = 15f, y = 0f))
        world.add(hero, Collider, Collider(Shape.Circle(4f)))

        val collisions = Collisions()
        collisions.detect(world, Events())
        assertPrints(collisions.pairCount, "1")

        // Scaled, the ring's radius would be 12 * 0.2 = 2.4, too small to reach the hero.
        world.get(ring, Collider).scalesWithTransform = true
        collisions.detect(world, Events())
        assertPrints(collisions.pairCount, "0")
    }
}
