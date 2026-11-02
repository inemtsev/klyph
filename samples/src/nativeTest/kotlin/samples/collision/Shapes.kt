package samples.collision

import klyph.collision.Shape
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Shapes {
    @Sample
    fun shapes() {
        val hull: Shape = Shape.Box(width = 32f, height = 16f)
        val shot: Shape = Shape.Circle(radius = 4f)
        assertPrints(hull, "Box(width=32.0, height=16.0)")
        assertPrints(shot, "Circle(radius=4.0)")
        assertTrue(shot == Shape.Circle(4f))
        assertFailsWith<IllegalArgumentException> { Shape.Circle(0f) }
    }
}
