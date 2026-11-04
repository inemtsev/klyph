package samples.math

import klyph.math.Rect
import klyph.math.Vec2
import klyph.math.center
import klyph.math.contains
import klyph.math.overlaps
import samples.Sample
import samples.assertPrints

class Rects {
    @Sample
    fun contains() {
        val level = Rect(x = 0f, y = 0f, width = 800f, height = 600f)
        assertPrints(level, "Rect(x=0.0, y=0.0, width=800.0, height=600.0)")
        assertPrints(level.center, "Vec2(400.0, 300.0)")
        assertPrints(Vec2(100f, 50f) in level, "true")
        assertPrints(level.contains(800f, 50f), "false")
    }

    @Sample
    fun overlaps() {
        val room = Rect(x = 0f, y = 0f, width = 10f, height = 10f)
        assertPrints(room.overlaps(Rect(x = 5f, y = 5f, width = 10f, height = 10f)), "true")
        assertPrints(room.overlaps(Rect(x = 10f, y = 0f, width = 10f, height = 10f)), "false")
    }
}
