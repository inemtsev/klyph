package klyph.render

/**
 * Names the coordinate system a quad is drawn in.
 *
 * A frame draws all [BACKGROUND] quads first, then all [WORLD] quads, then all [SCREEN] quads,
 * whatever their layers, so a sky always lies under the world and screen-space text and panels
 * always cover it. Within a space, quads sort by layer, lowest first.
 *
 * Drawing needs a display, so the sandbox draws in every space; the order is a sample.
 *
 * @sample samples.render.DrawSpaces.drawOrder
 */
public enum class DrawSpace {
    /**
     * Points from the window's top-left corner like [SCREEN], drawn before every [WORLD] quad, so
     * the camera has no effect and the quad lies under all that the world draws: a one-call sky,
     * a gradient or a picture that stays put. Entity sprites are never in this space. For a
     * backdrop that moves with the camera, slower than the world, give a sprite a [ParallaxLayer];
     * unlike a quad here, a layer with a factor of 0 zooms with the camera and sorts by its
     * [Sprite.layer] among the world's sprites.
     */
    BACKGROUND,

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
 * Writes the orthographic projection of [DrawSpace.SCREEN], which [DrawSpace.BACKGROUND] shares,
 * into the first 16 elements of [destination], column-major: point (0, 0) maps to the top-left
 * corner of clip space and ([width], [height]) to the bottom-right. Allocates nothing.
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
