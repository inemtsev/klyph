package klyph.render

/**
 * An ordered list of [regions] cut from one [texture], such as the frames of an animation or the
 * tiles of a tile set. Index `i` is region `i` of the list the sheet was created with.
 *
 * Use [SpriteSheet.Companion.fromGrid] to cut a texture into a uniform grid of cells.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 *
 * @throws IllegalArgumentException if a region is on a texture other than [texture].
 */
public class SpriteSheet(public val texture: Texture, regions: List<TextureRegion>) {
    /** The regions in index order. */
    public val regions: List<TextureRegion> = regions.toList()

    init {
        this.regions.forEachIndexed { index, region ->
            require(region.texture === texture) { "Region $index is on ${region.texture}, not on the sheet's $texture." }
        }
    }

    /** Number of regions. */
    public val size: Int get() = regions.size

    /**
     * Returns the region at [index], in `0 until size`.
     *
     * @throws IndexOutOfBoundsException if [index] is outside `0 until size`.
     */
    public operator fun get(index: Int): TextureRegion {
        if (index !in regions.indices) throw IndexOutOfBoundsException("Index $index is out of bounds for size $size.")
        return regions[index]
    }

    /** Returns `SpriteSheet(size=8, texture=Texture(128x64))`. */
    override fun toString(): String = "SpriteSheet(size=$size, texture=$texture)"

    /** Holds the [SpriteSheet] factories, such as [fromGrid]. */
    public companion object
}
