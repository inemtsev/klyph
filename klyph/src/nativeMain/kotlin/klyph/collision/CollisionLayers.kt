package klyph.collision

/**
 * A set of up to 32 collision layers, numbered 0 to 31, one bit each. A [Collider] uses one set
 * for the layers it is on and another for the layers it hits.
 *
 * Build a set from single layers with [of] and [plus]:
 * `CollisionLayers.of(1) + CollisionLayers.of(2)`. [bits] holds layer `i` in bit `i`.
 *
 * [toString] lists the layer numbers in ascending order, as `CollisionLayers(0, 2)`, and
 * [NONE] prints as `CollisionLayers()`.
 *
 * @sample samples.collision.CollisionLayerSets.collisionLayers
 */
public value class CollisionLayers(
    /** The layers as bits: layer `i` is in the set when bit `i` is set. */
    public val bits: Int,
) {
    /**
     * Returns the set of layers that are in this set, in [other], or in both.
     *
     * @sample samples.collision.CollisionLayerSets.collisionLayers
     */
    public operator fun plus(other: CollisionLayers): CollisionLayers = CollisionLayers(bits or other.bits)

    /**
     * Returns `true` if this set and [other] have at least one layer in common. With a single
     * layer as [other], that is whether the layer is in this set.
     *
     * @sample samples.collision.CollisionLayerSets.collisionLayers
     */
    public fun intersects(other: CollisionLayers): Boolean = bits and other.bits != 0

    override fun toString(): String {
        val builder = StringBuilder("CollisionLayers(")
        var first = true
        for (index in 0 until Int.SIZE_BITS) {
            if (bits and (1 shl index) == 0) continue
            if (!first) builder.append(", ")
            builder.append(index)
            first = false
        }
        return builder.append(')').toString()
    }

    /** Holds the common sets and builds the set of a single layer. */
    public companion object {
        /** Layer 0 alone, which every [Collider] is on unless it says otherwise. */
        public val DEFAULT: CollisionLayers = CollisionLayers(1)

        /** All 32 layers, which every [Collider] hits unless it says otherwise. */
        public val ALL: CollisionLayers = CollisionLayers(-1)

        /** No layer. A collider on no layer is never hit, and one with this mask hits nothing. */
        public val NONE: CollisionLayers = CollisionLayers(0)

        /**
         * Returns the set holding only layer [index], which must be between 0 and 31.
         *
         * @throws IllegalArgumentException if [index] is outside 0..31.
         *
         * @sample samples.collision.CollisionLayerSets.collisionLayers
         */
        public fun of(index: Int): CollisionLayers {
            require(index in 0 until Int.SIZE_BITS) { "Collision layer $index is not between 0 and 31." }
            return CollisionLayers(1 shl index)
        }
    }
}
