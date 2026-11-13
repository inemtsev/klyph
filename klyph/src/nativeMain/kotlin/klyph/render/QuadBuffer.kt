package klyph.render

import klyph.platform.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * A growable list of quads already in sprite record form, each with the texture, layer and
 * [DrawSpace] it sorts by. The canvas fills one, and the `Text` and particle extraction
 * another; the [SpriteRenderer] copies them into its record buffer after the entity sprites.
 *
 * Allocates only when it grows; [clear] keeps the arrays.
 */
internal class QuadBuffer(initialCapacity: Int = 64) {
    /** Number of quads held. */
    var count: Int = 0
        private set

    /** [SPRITE_RECORD_FLOATS] floats per quad, in the layout `sprite.vert.hlsl` reads. */
    var records = FloatArray(initialCapacity * SPRITE_RECORD_FLOATS)
        private set
    var layers = IntArray(initialCapacity)
        private set
    var textures = arrayOfNulls<Texture>(initialCapacity)
        private set
    var spaces = arrayOfNulls<DrawSpace>(initialCapacity)
        private set

    /**
     * Appends a quad of [width] by [height] centered on ([x], [y]), turned by [rotation] radians
     * about its center, showing [region] tinted by [color].
     */
    fun add(
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float,
        color: Color,
        layer: Int,
        space: DrawSpace,
    ) {
        val index = count
        if (index == layers.size) grow(index + 1)
        val base = index * SPRITE_RECORD_FLOATS
        val records = records
        records[base] = x
        records[base + 1] = y
        records[base + 2] = rotation
        records[base + 3] = 0f
        records[base + 4] = width
        records[base + 5] = height
        records[base + 6] = 0f
        records[base + 7] = 0f
        records[base + 8] = region.u0
        records[base + 9] = region.v0
        records[base + 10] = region.u1
        records[base + 11] = region.v1
        records[base + 12] = color.red
        records[base + 13] = color.green
        records[base + 14] = color.blue
        records[base + 15] = color.alpha
        layers[index] = layer
        textures[index] = region.texture
        spaces[index] = space
        count = index + 1
    }

    /**
     * Appends a quad like [add], placed so that the point at ([originX], [originY]) of the quad,
     * as fractions of its size from its top-left corner, lands on ([x], [y]) and the quad turns by
     * [rotation] about that point.
     */
    fun addAt(
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float,
        originX: Float,
        originY: Float,
        color: Color,
        layer: Int,
        space: DrawSpace,
    ) {
        // Offset from the origin to the center, turned the way the vertex shader turns corners.
        val dx = (0.5f - originX) * width
        val dy = (0.5f - originY) * height
        if (rotation == 0f) {
            add(region, x + dx, y + dy, width, height, 0f, color, layer, space)
        } else {
            val c = cos(rotation)
            val s = sin(rotation)
            add(region, x + dx * c - dy * s, y + dx * s + dy * c, width, height, rotation, color, layer, space)
        }
    }

    /**
     * Appends a quad like [addAt] that repeats the texture of [region] instead of showing the
     * region once: its texture coordinates run from the region's start up to `width / tileWidth`
     * across and `height / tileHeight` down, which a repeating sampler wraps. The caller checks
     * that [region] is the whole of its texture, with a repeating address, and that both tile
     * sizes are positive.
     */
    fun addTiledAt(
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tileWidth: Float,
        tileHeight: Float,
        rotation: Float,
        originX: Float,
        originY: Float,
        color: Color,
        layer: Int,
        space: DrawSpace,
    ) {
        addAt(region, x, y, width, height, rotation, originX, originY, color, layer, space)
        // The quad just added has the region's own far corner; replace it with the wrapped one.
        val base = (count - 1) * SPRITE_RECORD_FLOATS
        records[base + 10] = region.u0 + width / tileWidth
        records[base + 11] = region.v0 + height / tileHeight
    }

    /**
     * Appends [amount] quads of [texture] in [layer] of [space] and returns the index of the first.
     * Their records are left as they were; the caller writes all [SPRITE_RECORD_FLOATS] floats of
     * each into [records], read after this call because growing replaces the array.
     */
    fun reserve(amount: Int, texture: Texture, layer: Int, space: DrawSpace): Int {
        val first = count
        val end = first + amount
        if (end > layers.size) grow(end)
        layers.fill(layer, first, end)
        textures.fill(texture, first, end)
        spaces.fill(space, first, end)
        count = end
        return first
    }

    /** Removes every quad, keeping the arrays. */
    fun clear() {
        textures.fill(null, 0, count)
        spaces.fill(null, 0, count)
        count = 0
    }

    /** Returns the texture of quad [index]. */
    fun textureAt(index: Int): Texture = textures[index]!!

    /** Returns the space of quad [index]. */
    fun spaceAt(index: Int): DrawSpace = spaces[index]!!

    /** Doubles the arrays until they hold at least [minimum] quads. */
    private fun grow(minimum: Int) {
        var size = layers.size.coerceAtLeast(1)
        while (size < minimum) size *= 2
        records = records.copyOf(size * SPRITE_RECORD_FLOATS)
        layers = layers.copyOf(size)
        textures = textures.copyOf(size)
        spaces = spaces.copyOf(size)
    }

    override fun toString(): String = "QuadBuffer(count=$count)"
}
