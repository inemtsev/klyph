package klyph.render

/**
 * Returns the region of this texture whose top-left corner is [x] texels from the left edge and
 * [y] texels from the top edge, and which is [width] by [height] texels.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 *
 * @throws IllegalArgumentException if [width] or [height] is not positive, or if the rectangle
 *   does not lie inside the texture.
 */
public fun Texture.region(x: Int, y: Int, width: Int, height: Int): TextureRegion =
    TextureRegion(this, x = x, y = y, width = width, height = height)

/**
 * Returns a region that covers the whole texture, with texture coordinates from `0.0` to `1.0`.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 */
public fun Texture.asRegion(): TextureRegion = TextureRegion(this, x = 0, y = 0, width = width, height = height)
