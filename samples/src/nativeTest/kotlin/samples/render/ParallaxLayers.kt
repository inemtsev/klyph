package samples.render

import klyph.entity.Transform
import klyph.entity.World
import klyph.render.ParallaxLayer
import samples.Sample
import samples.assertPrints

class ParallaxLayers {
    @Sample
    fun parallaxLayer() {
        // Far hills: they follow the camera at a third of the world's speed, both ways, and repeat
        // every 1024 units along x, so the view never runs out of them.
        val hills = ParallaxLayer(factorX = 0.33f, repeatX = 1024f)
        assertPrints(hills, "ParallaxLayer(factor=0.33x0.33, repeat=1024.0x0.0)")

        // A sky that stays where it is on the screen, and a fence nearer than the world, which
        // scrolls faster than it along x and with it along y.
        val sky = ParallaxLayer(0f)
        val fence = ParallaxLayer(factorX = 1.5f, factorY = 1f, repeatX = 256f)
        assertPrints(sky, "ParallaxLayer(factor=0.0x0.0, repeat=0.0x0.0)")
        assertPrints(fence, "ParallaxLayer(factor=1.5x1.0, repeat=256.0x0.0)")

        // It goes on an entity with a Transform and a Sprite, and no parent. A Sprite needs a
        // texture, which needs a GPU, so this entity has only its Transform.
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform(x = 0f, y = 220f))
        world.add(entity, ParallaxLayer, hills)

        // The fields are variables: the same hills can move to the foreground.
        world.get(entity, ParallaxLayer).factorX = 1f
        assertPrints(hills.factorX, "1.0")
        assertPrints(ParallaxLayer.MAX_COPIES, "256")
    }
}
