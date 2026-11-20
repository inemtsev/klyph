package klyph.tiles

/**
 * A level made in the Tiled map editor, as `App.assets.tileMap` reads it from a `.tmj` file: its
 * tile [layers] in the order Tiled lists them from the bottom up, the one [tileSet] they share,
 * and the map's size in cells. Get a layer by its name with `map["ground"]`, then draw it with
 * `commands.spawnTileLayer(map["ground"])` and make walls of it with
 * `commands.spawnSolids(map["ground"])`.
 *
 * A map is read once and shared: `assets.tileMap` returns the same instance for the same file,
 * and nothing in it changes. To change cells while the game runs, change the [TileLayer] that
 * `spawnTileLayer` made, which is a copy.
 *
 * Object and image layers are not read; their names are in [skippedLayers], so a game can tell
 * that something it expected is missing. The tile layers inside a group layer are read as if they
 * stood in the map.
 *
 * [toString] gives `TileMap(level.tmj, size=40x20, layers=[background, ground], skipped=[spawns])`.
 *
 * Has no sample because reading a map needs its tileset image on a GPU device.
 */
public class TileMap internal constructor(
    /** The path the map was loaded from, as the game passed it, for messages. */
    internal val path: String,
    /** The tile set every layer's tiles come from. */
    public val tileSet: TileSet,
    /** Number of cells across, the map's width in Tiled. */
    public val columns: Int,
    /** Number of cells down, the map's height in Tiled. */
    public val rows: Int,
    layers: List<TileMapLayer>,
    skippedLayers: List<String>,
) {
    /** The tile layers in the order Tiled lists them from the bottom up, which is the order they draw in. */
    public val layers: List<TileMapLayer> = layers.toList()

    /** Names of the object and image layers the map has and klyph did not read, in file order. */
    public val skippedLayers: List<String> = skippedLayers.toList()

    /** Returns `TileMap(level.tmj, size=40x20, layers=[background, ground], skipped=[spawns])`. The format may change. */
    override fun toString(): String =
        "TileMap($path, size=${columns}x$rows, layers=${layers.map { it.name }}, skipped=$skippedLayers)"
}

/**
 * Returns the tile layer called [name], as Tiled names it in its Layers panel, as in
 * `map["ground"]`. When several layers share the name, the lowest one is returned.
 *
 * Has no sample because reading a map needs its tileset image on a GPU device.
 *
 * @throws NoSuchElementException if the map has no tile layer of that name; the message names the
 *   map file and the layers it has.
 */
public operator fun TileMap.get(name: String): TileMapLayer = layerOrNull(name) ?: throw NoSuchElementException(
    if (name in skippedLayers) {
        "Layer '$name' of $path is not a tile layer, so klyph skipped it; its tile layers are ${namesOf(layers)}."
    } else {
        "$path has no tile layer called '$name'; its tile layers are ${namesOf(layers)}."
    },
)

/**
 * Returns the tile layer called [name], or `null` if the map has none, for a layer a level may
 * leave out. When several layers share the name, the lowest one is returned.
 *
 * Has no sample because reading a map needs its tileset image on a GPU device.
 */
public fun TileMap.layerOrNull(name: String): TileMapLayer? = layers.firstOrNull { it.name == name }

/** Returns the names of [layers] quoted and separated by commas for a message, or "none". */
private fun namesOf(layers: List<TileMapLayer>): String =
    if (layers.isEmpty()) "none" else layers.joinToString(", ") { "'${it.name}'" }

/**
 * One tile layer of a [TileMap], read from the file and never changed: its [name], its cells
 * through [get], and the [isVisible], [opacity] and offset ([offsetX], [offsetY]) Tiled gave it. `Commands.spawnTileLayer`
 * draws it; `Commands.spawnSolids` makes walls and floors of it.
 *
 * [toString] gives `TileMapLayer('ground', size=40x20, tiles=312, offset=0.0x0.0)`.
 *
 * Has no sample because reading a map needs its tileset image on a GPU device.
 */
public class TileMapLayer internal constructor(
    /** The layer's name in Tiled's Layers panel. */
    public val name: String,
    /** The tile set of the map the layer belongs to. */
    override val tileSet: TileSet,
    /** Number of cells across. */
    override val columns: Int,
    /** Number of cells down. */
    override val rows: Int,
    /** The cells row by row, each the bits of its [Tile]. Never changed. */
    private val cells: IntArray,
    /** Whether the layer's eye is open in Tiled; `spawnTileLayer` copies it to [TileLayer.isVisible]. */
    public val isVisible: Boolean,
    /** The layer's opacity in Tiled, from 0 to 1; `spawnTileLayer` makes it the alpha of [TileLayer.color]. */
    public val opacity: Float,
    /** The layer's horizontal offset in Tiled, with those of its groups; `spawnTileLayer` and `spawnSolids` add it to `x`. */
    public val offsetX: Float = 0f,
    /** The layer's vertical offset in Tiled, with those of its groups; `spawnTileLayer` and `spawnSolids` add it to `y`. */
    public val offsetY: Float = 0f,
) : TileGrid {
    /**
     * Returns the tile in the cell at [column] and [row], [Tile.EMPTY] when the cell has none.
     *
     * @throws IndexOutOfBoundsException if [column] is not in `0 until columns` or [row] is not in
     *   `0 until rows`.
     */
    override operator fun get(column: Int, row: Int): Tile {
        if (column !in 0 until columns || row !in 0 until rows) {
            throw IndexOutOfBoundsException("Cell ($column, $row) is outside layer '$name' of ${columns}x$rows cells.")
        }
        return Tile(cells[row * columns + column])
    }

    /** Returns `TileMapLayer('ground', size=40x20, tiles=312, offset=0.0x0.0)`. The format may change. */
    override fun toString(): String =
        "TileMapLayer('$name', size=${columns}x$rows, tiles=${cells.count { it != 0 }}, offset=${offsetX}x$offsetY)"
}
