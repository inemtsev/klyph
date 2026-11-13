package samples.render

import klyph.render.DrawSpace
import samples.Sample
import samples.assertPrints

class DrawSpaces {
    @Sample
    fun drawOrder() {
        // A frame draws every BACKGROUND quad, then every WORLD quad, then every SCREEN quad,
        // whatever their layers; only within a space do layers decide.
        assertPrints(DrawSpace.entries, "[BACKGROUND, WORLD, SCREEN]")
    }
}
