package klyph.tiles

/**
 * One cell of a tile layer: which tile of the [TileSet] it shows, by [id], and whether that tile
 * is mirrored or turned. [Tile.EMPTY] is a cell with no tile, which draws nothing and is never
 * solid. Make a tile with [Tile.of], as in `layer[3, 4] = Tile.of(2)`.
 *
 * A tile's [id] is its index in the tile set, counted from 0 at the top-left tile of the tileset
 * image, row by row, which is the ID Tiled shows for a tile in its Properties panel.
 *
 * The flips follow Tiled: [isFlippedDiagonally] swaps the tile's top-right and bottom-left
 * corners, which turns a picture a quarter turn and mirrors it, and is applied first, then
 * [isFlippedX] mirrors left to right and [isFlippedY] top to bottom. Tiled's rotate buttons store a
 * quarter turn clockwise as a diagonal flip plus a flip in x. [flippedX], [flippedY] and
 * [flippedDiagonally] return a copy with one flip toggled.
 *
 * [toString] gives `Tile(2)`, `Tile(2, flippedX, flippedDiagonally)` or `Tile.EMPTY`.
 *
 * @sample samples.tiles.Tiles.tile
 */
public value class Tile internal constructor(internal val bits: Int) {
    /**
     * Index of the tile in its tile set, from 0, without the flips; -1 for [Tile.EMPTY]. Two tiles
     * with the same id show the same picture, flipped or not.
     */
    public val id: Int get() = (bits and ID_MASK) - 1

    /** Whether this is [Tile.EMPTY], a cell with no tile, whatever flips it carries. */
    public val isEmpty: Boolean get() = bits and ID_MASK == 0

    /** Whether the tile is mirrored left to right, after any diagonal flip. */
    public val isFlippedX: Boolean get() = bits and FLIPPED_X != 0

    /** Whether the tile is mirrored top to bottom, after any diagonal flip. */
    public val isFlippedY: Boolean get() = bits and FLIPPED_Y != 0

    /** Whether the tile's top-right and bottom-left corners are swapped, before the other flips. */
    public val isFlippedDiagonally: Boolean get() = bits and FLIPPED_DIAGONALLY != 0

    /** Returns `Tile(2)`, with the flips it has after the id, or `Tile.EMPTY`. The format may change. */
    override fun toString(): String {
        if (isEmpty) return "Tile.EMPTY"
        val flips = buildString {
            if (isFlippedX) append(", flippedX")
            if (isFlippedY) append(", flippedY")
            if (isFlippedDiagonally) append(", flippedDiagonally")
        }
        return "Tile($id$flips)"
    }

    /** Holds [EMPTY] and makes tiles from the ids users think in through [of]. */
    public companion object {
        /** The cell with no tile: it draws nothing, is never solid, and its [id] is -1. */
        public val EMPTY: Tile = Tile(0)

        /** The largest id [of] accepts, 268 435 454, the most a Tiled tile id can hold once its flags are taken out. */
        public const val MAX_ID: Int = 0x0FFFFFFE

        /**
         * Returns the tile with index [id] in its tile set, not flipped. [id] counts from 0 at the
         * top-left tile of the tileset image, row by row, as Tiled numbers tiles.
         *
         * @throws IllegalArgumentException if [id] is negative or above [MAX_ID].
         *
         * @sample samples.tiles.Tiles.tile
         */
        public fun of(id: Int): Tile {
            require(id in 0..MAX_ID) { "Tile id $id is not between 0 and $MAX_ID." }
            return Tile(id + 1)
        }

        /** Bit of a Tiled id that mirrors the tile left to right. */
        internal const val FLIPPED_X: Int = Int.MIN_VALUE

        /** Bit of a Tiled id that mirrors the tile top to bottom. */
        internal const val FLIPPED_Y: Int = 0x40000000

        /** Bit of a Tiled id that swaps the tile's top-right and bottom-left corners. */
        internal const val FLIPPED_DIAGONALLY: Int = 0x20000000

        /** The bits of a Tiled id that hold the tile, one more than [id]; Tiled keeps the four above for flags. */
        internal const val ID_MASK: Int = 0x0FFFFFFF

        /** The three flip bits together. */
        internal const val FLIPS: Int = FLIPPED_X or FLIPPED_Y or FLIPPED_DIAGONALLY
    }
}

/**
 * Returns this tile mirrored left to right: with [Tile.isFlippedX] toggled and the rest kept.
 * [Tile.EMPTY] stays empty.
 *
 * @sample samples.tiles.Tiles.flipped
 */
public fun Tile.flippedX(): Tile = if (isEmpty) Tile.EMPTY else Tile(bits xor Tile.FLIPPED_X)

/**
 * Returns this tile mirrored top to bottom: with [Tile.isFlippedY] toggled and the rest kept.
 * [Tile.EMPTY] stays empty.
 *
 * @sample samples.tiles.Tiles.flipped
 */
public fun Tile.flippedY(): Tile = if (isEmpty) Tile.EMPTY else Tile(bits xor Tile.FLIPPED_Y)

/**
 * Returns this tile with its top-right and bottom-left corners swapped: with
 * [Tile.isFlippedDiagonally] toggled and the rest kept. `tile.flippedDiagonally().flippedX()` is
 * the tile turned a quarter turn clockwise, as Tiled's rotate button stores it. [Tile.EMPTY]
 * stays empty.
 *
 * @sample samples.tiles.Tiles.flipped
 */
public fun Tile.flippedDiagonally(): Tile = if (isEmpty) Tile.EMPTY else Tile(bits xor Tile.FLIPPED_DIAGONALLY)
