package samples.math

import klyph.math.Vec2
import klyph.math.dot
import klyph.math.length
import samples.Sample
import samples.assertPrints

class Vec2s {
    @Sample
    fun operators() {
        val position = Vec2(10f, 20f)
        val velocity = Vec2(3f, -4f)
        assertPrints(position + velocity, "Vec2(13.0, 16.0)")
        assertPrints(position - velocity, "Vec2(7.0, 24.0)")
        assertPrints(velocity * 2f, "Vec2(6.0, -8.0)")
        assertPrints(-velocity, "Vec2(-3.0, 4.0)")
        assertPrints(Vec2.ZERO, "Vec2(0.0, 0.0)")
    }

    @Sample
    fun length() {
        assertPrints(Vec2(3f, 4f).length, "5.0")
    }

    @Sample
    fun dot() {
        val right = Vec2(1f, 0f)
        assertPrints(right.dot(Vec2(5f, 2f)), "5.0")
        assertPrints(right.dot(Vec2(0f, 7f)), "0.0")
    }
}
