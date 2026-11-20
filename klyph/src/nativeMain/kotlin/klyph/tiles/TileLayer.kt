package klyph.tiles

import klyph.entity.ComponentType
import klyph.platform.Color

/**
 * A grid of tiles drawn as one picture: put it on an entity with a `Transform` and the engine
 * draws every cell's tile from [tileSet], cell (0, 0) at the transform, columns along +x and rows
 * along +y (down), each cell `tileSet.tileWidth` by `tileSet.tileHeight` world units times the
 * transform's scale. `Commands.spawnTileLayer` spawns one from a layer of a Tiled map;
 * `TileLayer(tileSet, columns, rows)` makes an empty one to fill in code with [set].
 *
 * Only the cells the camera sees are drawn, each frame, so a layer may be far larger than the
 * window, and a change made with [set] shows in the next frame. The cells draw in sprite [layer]
 * with sprites, tinted by [color], all from one texture, so a layer costs one draw call when
 * nothing of another texture shares its sprite layer. A layer whose [isVisible] is `false` is not
 * drawn. A layer of a scene frozen under a pushed scene keeps drawing, as sprites do.
 *
 * The entity must be a root entity whose transform is not rotated and whose scale is positive:
 * drawing a layer throws [IllegalStateException], naming the entity, when it has a parent or a
 * rotation other than 0, or a scale that is not positive and finite. A layer on an `Interpolated`
 * entity is drawn between its last two fixed steps, as a sprite is.
 *
 * Changing cells does not change the solids made from them: `Commands.spawnSolids` spawns solids
 * for the cells as they are when it is called.
 *
 * [toString] gives `TileLayer(size=20x12, tiles=57, layer=0, color=Color(#ffffffff), visible=true,
 * tileSet=TileSet(...))`.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 *
 * @throws IllegalArgumentException if [columns] or [rows] is not positive, or the layer would
 *   have more than [MAX_CELLS] cells.
 */
public class TileLayer(
    /** The tile set every cell's tile belongs to. */
    override val tileSet: TileSet,
    /** Number of cells across. */
    override val columns: Int,
    /** Number of cells down. */
    override val rows: Int,
    /** Tint multiplied with every tile. [Color.WHITE] shows the tiles unchanged; lower its alpha to fade the layer. */
    public var color: Color = Color.WHITE,
    /** The sprite layer the tiles draw in, as `Sprite.layer`: higher layers draw on top. */
    public var layer: Int = 0,
    /** Whether the layer is drawn. */
    public var isVisible: Boolean = true,
) : TileGrid {
    init {
        require(columns > 0 && rows > 0) { "Tile layer size ${columns}x$rows is not positive." }
        require(columns.toLong() * rows <= MAX_CELLS) { "Tile layer size ${columns}x$rows is more than $MAX_CELLS cells." }
    }

    /** The cells row by row, each the bits of its [Tile]. The renderer reads them directly. */
    internal val cells: IntArray = IntArray(columns * rows)

    /** Number of cells that hold a tile, kept by [set]; the renderer reserves records for no more than this. */
    internal var tileCount: Int = 0
        private set

    /** Replaces every cell with [source], cells of the same size and tile set, of which [tiles] hold a tile. */
    internal fun copyCellsFrom(source: IntArray, tiles: Int) {
        source.copyInto(cells)
        tileCount = tiles
    }

    /**
     * Returns the tile in the cell at [column] and [row], [Tile.EMPTY] when the cell has none.
     *
     * @throws IndexOutOfBoundsException if [column] is not in `0 until columns` or [row] is not in
     *   `0 until rows`.
     */
    override operator fun get(column: Int, row: Int): Tile = Tile(cells[indexOf(column, row)])

    /**
     * Puts [tile] in the cell at [column] and [row], or empties the cell when [tile] is
     * [Tile.EMPTY], as in `layer[3, 4] = Tile.of(2)`. The next frame draws it.
     *
     * @throws IndexOutOfBoundsException if [column] is not in `0 until columns` or [row] is not in
     *   `0 until rows`.
     * @throws IllegalArgumentException if [tile] is not empty and its id is not in
     *   `0 until tileSet.size`.
     */
    public operator fun set(column: Int, row: Int, tile: Tile) {
        val index = indexOf(column, row)
        require(tile.isEmpty || tile.id < tileSet.size) {
            "Tile id ${tile.id} is not between 0 and ${tileSet.size - 1}, the ids of the tile set."
        }
        val bits = if (tile.isEmpty) 0 else tile.bits
        if (cells[index] == 0) {
            if (bits != 0) tileCount++
        } else if (bits == 0) {
            tileCount--
        }
        cells[index] = bits
    }

    private fun indexOf(column: Int, row: Int): Int {
        if (column !in 0 until columns || row !in 0 until rows) {
            throw IndexOutOfBoundsException("Cell ($column, $row) is outside the tile layer of ${columns}x$rows cells.")
        }
        return row * columns + column
    }

    /**
     * Returns `TileLayer(size=20x12, tiles=57, layer=0, color=Color(#ffffffff), visible=true, tileSet=TileSet(...))`.
     * The format may change.
     */
    override fun toString(): String =
        "TileLayer(size=${columns}x$rows, tiles=$tileCount, layer=$layer, color=$color, visible=$isVisible, tileSet=$tileSet)"

    /** Component key for [TileLayer], and the limit on its size. */
    public companion object : ComponentType<TileLayer>("TileLayer") {
        /** The most cells a layer may have, 16 777 216, such as 4096 by 4096. */
        public const val MAX_CELLS: Int = 1 shl 24
    }
}
