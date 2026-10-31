package klyph.render

/**
 * A rectangle of [width] by [height] texels inside [texture] whose top-left corner is [x] texels
 * from the texture's left edge and [y] texels from its top edge. A [Sprite] shows one region.
 *
 * [u0], [v0], [u1] and [v1] are the rectangle's edges as texture coordinates in `0.0..1.0`, the
 * form the GPU samples with.
 *
 * Two regions are equal when they cover the same rectangle of the same [Texture] instance.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 *
 * @throws IllegalArgumentException if [width] or [height] is not positive, or if the rectangle
 *   does not lie inside [texture].
 *
 * @see Texture.region
 * @see Texture.asRegion
 */
public class TextureRegion(
    public val texture: Texture,
    public val x: Int,
    public val y: Int,
    public val width: Int,
    public val height: Int,
) {
    init {
        require(width > 0) { "Region width $width is not positive." }
        require(height > 0) { "Region height $height is not positive." }
        require(x >= 0 && y >= 0 && x <= texture.width - width && y <= texture.height - height) {
            "Region ${width}x$height at ($x, $y) does not fit in $texture."
        }
    }

    /** Left edge as a texture coordinate: [x] divided by the texture width. */
    public val u0: Float = x.toFloat() / texture.width

    /** Top edge as a texture coordinate: [y] divided by the texture height. */
    public val v0: Float = y.toFloat() / texture.height

    /** Right edge as a texture coordinate: `x + width` divided by the texture width. */
    public val u1: Float = (x + width).toFloat() / texture.width

    /** Bottom edge as a texture coordinate: `y + height` divided by the texture height. */
    public val v1: Float = (y + height).toFloat() / texture.height

    override fun equals(other: Any?): Boolean =
        other is TextureRegion && texture === other.texture &&
            x == other.x && y == other.y && width == other.width && height == other.height

    override fun hashCode(): Int {
        var result = texture.hashCode()
        result = 31 * result + x
        result = 31 * result + y
        result = 31 * result + width
        result = 31 * result + height
        return result
    }

    /** Returns `TextureRegion(x=0, y=0, size=32x32, texture=Texture(256x256))`. */
    override fun toString(): String = "TextureRegion(x=$x, y=$y, size=${width}x$height, texture=$texture)"
}
