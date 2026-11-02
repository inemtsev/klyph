package klyph.collision

import klyph.entity.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ShapeTest {
    @Test
    fun boxEqualityAndToString() {
        assertEquals(Shape.Box(width = 16f, height = 8f), Shape.Box(width = 16f, height = 8f))
        assertEquals(Shape.Box(width = 16f, height = 8f).hashCode(), Shape.Box(width = 16f, height = 8f).hashCode())
        assertNotEquals(Shape.Box(width = 16f, height = 8f), Shape.Box(width = 8f, height = 16f))
        assertEquals("Box(width=16.0, height=8.0)", Shape.Box(width = 16f, height = 8f).toString())
    }

    @Test
    fun circleEqualityAndToString() {
        assertEquals(Shape.Circle(4f), Shape.Circle(4f))
        assertEquals(Shape.Circle(4f).hashCode(), Shape.Circle(4f).hashCode())
        assertNotEquals<Shape>(Shape.Circle(4f), Shape.Box(width = 8f, height = 8f))
        assertEquals("Circle(radius=4.0)", Shape.Circle(4f).toString())
    }

    @Test
    fun sizesMustBePositive() {
        assertEquals("Box width 0.0 is not positive.", assertFailsWith<IllegalArgumentException> { Shape.Box(width = 0f, height = 1f) }.message)
        assertEquals("Box height -1.0 is not positive.", assertFailsWith<IllegalArgumentException> { Shape.Box(width = 1f, height = -1f) }.message)
        assertEquals("Circle radius 0.0 is not positive.", assertFailsWith<IllegalArgumentException> { Shape.Circle(0f) }.message)
        assertFailsWith<IllegalArgumentException> { Shape.Circle(Float.NaN) }
    }

    @Test
    fun colliderDefaultsAndToString() {
        val collider = Collider(Shape.Circle(8f))
        assertEquals(CollisionLayers.DEFAULT, collider.layers)
        assertEquals(CollisionLayers.ALL, collider.mask)
        assertEquals(0f, collider.offsetX)
        assertEquals(
            "Collider(shape=Box(width=4.0, height=2.0), offset=1.0 x -2.0, layers=CollisionLayers(0), mask=CollisionLayers(1))",
            Collider(Shape.Box(width = 4f, height = 2f), offsetX = 1f, offsetY = -2f, mask = CollisionLayers.of(1)).toString(),
        )
    }

    @Test
    fun overlapEqualityAndToString() {
        val world = World()
        val a = world.create()
        val b = world.create()
        assertEquals(Overlap(a, b), Overlap(a, b))
        assertEquals(Overlap(a, b).hashCode(), Overlap(a, b).hashCode())
        assertNotEquals(Overlap(a, b), Overlap(b, a))
        assertEquals("Overlap(a=Entity(0:0), b=Entity(1:0))", Overlap(a, b).toString())
    }
}
