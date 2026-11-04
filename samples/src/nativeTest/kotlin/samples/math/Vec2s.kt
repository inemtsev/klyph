package samples.math

import klyph.math.Vec2
import klyph.math.angle
import klyph.math.degrees
import klyph.math.distanceTo
import klyph.math.dot
import klyph.math.length
import klyph.math.lengthSquared
import klyph.math.lerp
import klyph.math.normalized
import klyph.math.toDegrees
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
        assertPrints(Vec2(3f, 4f).lengthSquared, "25.0")
    }

    @Sample
    fun normalized() {
        assertPrints(Vec2(3f, 4f).normalized(), "Vec2(0.6, 0.8)")
        assertPrints(Vec2.ZERO.normalized(), "Vec2(0.0, 0.0)")
    }

    @Sample
    fun distanceTo() {
        val player = Vec2(1f, 1f)
        val enemy = Vec2(4f, 5f)
        assertPrints(player.distanceTo(enemy), "5.0")
    }

    @Sample
    fun fromAngle() {
        val down = Vec2.fromAngle(90f.degrees, length = 2f)
        assertPrints(down.y, "2.0")
        assertPrints(Vec2(0f, 2f).angle.toDegrees(), "90.0")
        assertPrints(Vec2(1f, 0f).angle, "0.0")
    }

    @Sample
    fun lerp() {
        val from = Vec2(0f, 10f)
        val to = Vec2(10f, 20f)
        assertPrints(from.lerp(to, 0.5f), "Vec2(5.0, 15.0)")
        assertPrints(from.lerp(to, 1f), "Vec2(10.0, 20.0)")
    }

    @Sample
    fun dot() {
        val right = Vec2(1f, 0f)
        assertPrints(right.dot(Vec2(5f, 2f)), "5.0")
        assertPrints(right.dot(Vec2(0f, 7f)), "0.0")
    }
}
