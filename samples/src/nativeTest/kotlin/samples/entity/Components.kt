package samples.entity

import klyph.entity.ComponentType
import klyph.entity.Transform
import klyph.entity.World
import samples.Sample
import samples.assertPrints

// Declared at file level because Kotlin does not allow a companion object in a local class.
class Health(var points: Int) {
    companion object : ComponentType<Health>("Health")
}

class Components {
    @Sample
    fun componentType() {
        val world = World()
        val entity = world.create()
        world.add(entity, Health, Health(10))
        assertPrints(world.get(entity, Health).points, "10")
        assertPrints(Health, "ComponentType(Health)")
    }

    @Sample
    fun entity() {
        val world = World()
        val first = world.create()
        world.destroy(first)
        val reused = world.create()
        assertPrints(first, "Entity(0:0)")
        assertPrints(reused, "Entity(0:1)")
        assertPrints(reused.generation, "1")
    }

    @Sample
    fun transform() {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform(x = 1f, y = 2f))
        world.get(entity, Transform).x += 4f
        assertPrints(world.get(entity, Transform), "Transform(x=5.0, y=2.0, rotation=0.0, scale=1.0 x 1.0)")
    }
}
