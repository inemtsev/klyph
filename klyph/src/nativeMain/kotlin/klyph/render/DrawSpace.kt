package klyph.render

/**
 * Names the coordinate system a quad is drawn in.
 *
 * All [WORLD] quads of a frame are drawn before all [SCREEN] quads, so screen-space text and
 * panels always cover the world. Within a space, quads sort by layer, lowest first.
 *
 * Samples live in the sandbox because drawing needs a display.
 */
public enum class DrawSpace {
    /**
     * World units seen through `App.camera`: the quad moves, zooms and turns with the camera. Entity
     * sprites and `Text` components are always drawn in this space.
     */
    WORLD,

    /**
     * Points from the window's top-left corner, +y down, the unit `Window.width` and mouse
     * positions use. The camera has no effect, so this is the space for a HUD, menus and debug text.
     */
    SCREEN,
}

/**
 * Writes the orthographic projection of [DrawSpace.SCREEN] into the first 16 elements of
 * [destination], column-major: point (0, 0) maps to the top-left corner of clip space and
 * ([width], [height]) to the bottom-right. Allocates nothing.
 */
internal fun writeScreenProjection(destination: FloatArray, width: Float, height: Float) {
    require(destination.size >= 16) { "Projection destination size ${destination.size} is less than 16." }
    require(width > 0f && height > 0f) { "Screen size $width x $height is not positive." }
    destination.fill(0f, 0, 16)
    destination[0] = 2f / width
    destination[5] = -2f / height
    destination[10] = 1f
    destination[12] = -1f
    destination[13] = 1f
    destination[15] = 1f
}
