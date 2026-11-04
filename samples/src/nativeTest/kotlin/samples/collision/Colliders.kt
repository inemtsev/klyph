package samples.collision

import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Shape
import klyph.entity.Transform
import klyph.entity.World
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
}
