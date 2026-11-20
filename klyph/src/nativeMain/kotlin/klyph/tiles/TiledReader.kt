package klyph.tiles

import klyph.json.JsonException
import klyph.json.JsonObject
import klyph.json.parseJson
import klyph.platform.FileException
import klyph.platform.Files
import klyph.platform.ImageException
import klyph.render.SpriteSheet
import klyph.render.Texture
import klyph.render.fromGrid
import kotlin.io.encoding.Base64

/**
 * Reads the Tiled JSON map [text], the file at [path] as the game named it, which lies at
 * [resolved], and returns it as a [TileMap]. [readText] reads an external tileset file at a
 * resolved path, and [loadTexture] loads the tileset image at a resolved path; whatever either
 * throws is wrapped in a [TileMapException] naming the file.
 *
 * Reads orthogonal, finite maps with one tileset, embedded or in a JSON `.tsj` file, whose tile
 * layers hold their data as a CSV array or as uncompressed base64. The tileset's image and an
 * external tileset resolve against the folder of the file that names them, as Tiled writes them.
 * Group layers are read through the tile layers inside them, their offsets added to the layers'
 * own; object and image layers are listed in [TileMap.skippedLayers]. A tile layer's `offsetx` and
 * `offsety` are kept in [TileMapLayer.offsetX] and [TileMapLayer.offsetY]. Every other feature is
 * rejected with a message that names the file and says what to change in Tiled.
 *
 * @throws TileMapException if the map cannot be read; see above.
 */
internal fun readTiledMap(
    path: String,
    resolved: String,
    text: String,
    readText: (String) -> String,
    loadTexture: (String) -> Texture,
): TileMap = TiledReader(path, resolved, readText, loadTexture).read(text)

/** One reading of a map file, holding the paths its messages and relative paths need. */
private class TiledReader(
    private val path: String,
    private val resolved: String,
    private val readText: (String) -> String,
    private val loadTexture: (String) -> Texture,
) {
    fun read(text: String): TileMap {
        val map = parseObject(text, path, MAP_FIX)
        try {
            checkMap(map)
            val columns = positive(map, "width")
            val rows = positive(map, "height")
            val tileset = readTileset(map)
            val layers = ArrayList<TileMapLayer>()
            val skipped = ArrayList<String>()
            readLayers(map, tileset, 0f, 0f, layers, skipped)
            return TileMap(path, tileset.tileSet, columns, rows, layers, skipped)
        } catch (e: JsonException) {
            throw TileMapException(e.message ?: "$path could not be read.", e)
        }
    }

    /**
     * Reads the layers of [parent], the map or a group layer, into [layers] and [skipped], each
     * moved by [offsetX] and [offsetY], the sum of the offsets of the groups it is inside.
     */
    private fun readLayers(
        parent: JsonObject,
        tileset: MapTileset,
        offsetX: Float,
        offsetY: Float,
        layers: MutableList<TileMapLayer>,
        skipped: MutableList<String>,
    ) {
        for (layer in parent.objects("layers") { index, item -> layerContext(index, item) }) {
            when (val type = layer.string("type")) {
                "tilelayer" -> {
                    checkLayerFeatures(layer)
                    val x = offsetX + layer.floatOrDefault("offsetx", 0f)
                    val y = offsetY + layer.floatOrDefault("offsety", 0f)
                    layers += readLayer(layer, tileset, x, y)
                }
                "group" -> {
                    checkLayerFeatures(layer)
                    val groupX = offsetX + layer.floatOrDefault("offsetx", 0f)
                    val groupY = offsetY + layer.floatOrDefault("offsety", 0f)
                    readLayers(layer, tileset, groupX, groupY, layers, skipped)
                }
                "objectgroup", "imagelayer" -> skipped += layer.stringOrNull("name").orEmpty()
                else -> layer.fail("has the type '$type', which klyph does not know")
            }
        }
    }

    /** Rejects the kinds of map klyph does not read. */
    private fun checkMap(map: JsonObject) {
        val orientation = map.string("orientation")
        if (orientation != "orthogonal") {
            map.fail(
                "is an $orientation map, and klyph reads orthogonal maps only",
                "set Orientation to Orthogonal in Tiled's Map Properties",
            )
        }
        if (map.booleanOrNull("infinite") == true) {
            map.fail("is an infinite map, which klyph does not read", "untick Infinite in Tiled's Map Properties and save it again")
        }
    }

    private fun layerContext(index: Int, item: JsonObject): String {
        val name = item.stringOrNull("name")
        return if (name != null) "Layer '$name' of $path" else "Layer $index of $path"
    }

    /** The tile set of the map and the first id its tiles have in the map's layers. */
    private class MapTileset(val tileSet: TileSet, val firstId: Int, val displayPath: String)

    private fun readTileset(map: JsonObject): MapTileset {
        val entries = map.objects("tilesets") { _, _ -> "The tileset of $path" }
        if (entries.isEmpty()) map.fail("has no tileset", "add a tileset to the map in Tiled")
        if (entries.size > 1) {
            map.fail(
                "uses ${entries.size} tilesets, and klyph reads one per map",
                "put every tile the map uses in one tileset, or split the map",
            )
        }
        val entry = entries[0]
        val firstId = entry.int("firstgid")
        val source = entry.stringOrNull("source")
            ?: return MapTileset(readTilesetFields(entry, resolved, "the tileset of $path", path, map), firstId, path)
        val sourcePath = against(resolved, source)
        val sourceDisplay = against(path, source)
        if (source.endsWith(".tsx", ignoreCase = true)) {
            entry.fail(
                "is the XML file $sourceDisplay, and klyph reads JSON tilesets only",
                "export the tileset as JSON (.tsj) from Tiled and point the map at it, or embed the tileset in the map",
            )
        }
        val text = try {
            readText(sourcePath)
        } catch (e: FileException) {
            throw TileMapException("The tileset $sourceDisplay of $path could not be read: ${e.message}", e)
        }
        val tileset = parseObject(text, sourceDisplay, "save the tileset again from Tiled as JSON")
        return MapTileset(readTilesetFields(tileset, sourcePath, "tileset $sourceDisplay", sourceDisplay, map), firstId, sourceDisplay)
    }

    /**
     * Reads the fields of [tileset], which is the file at [file] or embedded in the map, called
     * [label] inside messages and shown as [display] where a path is wanted, and loads its image
     * from the folder of [file].
     */
    private fun readTilesetFields(tileset: JsonObject, file: String, label: String, display: String, map: JsonObject): TileSet {
        val image = tileset.stringOrNull("image") ?: tileset.fail(
            "has no image, as a tileset made of separate images has",
            "make the tileset from one image (Type: Based on Tileset Image) in Tiled",
        )
        val tileWidth = positive(tileset, "tilewidth")
        val tileHeight = positive(tileset, "tileheight")
        val mapTileWidth = positive(map, "tilewidth")
        val mapTileHeight = positive(map, "tileheight")
        if (tileWidth != mapTileWidth || tileHeight != mapTileHeight) {
            tileset.fail(
                "has tiles of ${tileWidth}x$tileHeight but the map's tiles are ${mapTileWidth}x$mapTileHeight",
                "make them the same size in Tiled",
            )
        }
        val columns = positive(tileset, "columns")
        val count = positive(tileset, "tilecount")
        checkTileOffset(tileset)
        val texture = try {
            loadTexture(against(file, image))
        } catch (e: FileException) {
            throw imageFailure(display, image, label, e)
        } catch (e: ImageException) {
            throw imageFailure(display, image, label, e)
        }
        val sheet = cutSheet(tileset, texture, columns, count, tileWidth, tileHeight)
        val solid = HashSet<Int>()
        val oneWay = HashSet<Int>()
        readFlags(tileset, label, count, solid, oneWay)
        return TileSet(sheet, tileWidth.toFloat(), tileHeight.toFloat(), solid, oneWay)
    }

    /** Adds the ids of the tiles of [tileset] whose `solid` property is true to [solid], and those whose `oneWay` one is to [oneWay]. */
    private fun readFlags(tileset: JsonObject, label: String, count: Int, solid: MutableSet<Int>, oneWay: MutableSet<Int>) {
        for (tile in tileset.objects("tiles") { index, _ -> "Tile entry $index of $label" }) {
            val id = tile.int("id")
            if (id !in 0 until count) continue
            for (property in tile.objects("properties") { _, item -> "Property '${item.stringOrNull("name")}' of tile $id in $label" }) {
                when (property.stringOrNull("name")) {
                    "solid" -> if (flag(property)) solid += id
                    "oneWay" -> if (flag(property)) oneWay += id
                }
            }
        }
    }

    private fun imageFailure(display: String, image: String, label: String, cause: Exception): TileMapException =
        TileMapException(
            "The image ${against(display, image)} of $label could not be loaded (${cause.message.orEmpty().removeSuffix(".")}); " +
                "put it where the tileset names it, relative to the tileset's file.",
            cause,
        )

    private fun cutSheet(tileset: JsonObject, texture: Texture, columns: Int, count: Int, tileWidth: Int, tileHeight: Int): SpriteSheet {
        val rows = (count + columns - 1) / columns
        val grid = try {
            SpriteSheet.fromGrid(
                texture,
                columns = columns,
                rows = rows,
                margin = tileset.intOrNull("margin") ?: 0,
                spacing = tileset.intOrNull("spacing") ?: 0,
                cellWidth = tileWidth,
                cellHeight = tileHeight,
            )
        } catch (e: IllegalArgumentException) {
            throw TileMapException(
                "${tileset.context} needs $columns by $rows tiles of ${tileWidth}x$tileHeight " +
                    "but its image is ${texture.width}x${texture.height}; " +
                    "check the tile size, margin and spacing in Tiled's Tileset Properties.",
                e,
            )
        }
        return SpriteSheet(texture, grid.regions.take(count))
    }

    private fun readLayer(layer: JsonObject, tileset: MapTileset, offsetX: Float, offsetY: Float): TileMapLayer {
        val compression = layer.stringOrNull("compression").orEmpty()
        if (compression.isNotEmpty()) {
            layer.fail(
                "is compressed with $compression",
                "export the map with Tile Layer Format CSV, or Base64 (uncompressed), in Tiled's Map Properties",
            )
        }
        val columns = positive(layer, "width")
        val rows = positive(layer, "height")
        if (columns.toLong() * rows > TileLayer.MAX_CELLS) layer.fail("has more than ${TileLayer.MAX_CELLS} cells", "split the map")
        val ids = when (val encoding = layer.stringOrNull("encoding") ?: "csv") {
            "csv" -> csvIds(layer, columns * rows)
            "base64" -> base64Ids(layer, columns * rows)
            else -> layer.fail("has data in the encoding '$encoding'", CSV_FIX)
        }
        val cells = IntArray(ids.size)
        for (index in ids.indices) cells[index] = cellOf(layer, ids[index], index % columns, index / columns, tileset)
        val opacity = (layer.doubleOrNull("opacity") ?: 1.0).toFloat()
        val isVisible = layer.booleanOrNull("visible") ?: true
        val name = layer.stringOrNull("name").orEmpty()
        return TileMapLayer(name, tileset.tileSet, columns, rows, cells, isVisible, opacity, offsetX, offsetY)
    }

    /** Turns the Tiled id [id] of the cell at [column] and [row] into the bits of its [Tile]. */
    private fun cellOf(layer: JsonObject, id: Int, column: Int, row: Int, tileset: MapTileset): Int {
        val withoutFlags = id and Tile.ID_MASK
        if (withoutFlags == 0) return 0
        val local = withoutFlags - tileset.firstId
        if (local !in 0 until tileset.tileSet.size) {
            layer.fail(
                "has the tile id $withoutFlags at column $column, row $row, which is not in its tileset ${tileset.displayPath} " +
                    "(ids ${tileset.firstId} to ${tileset.firstId + tileset.tileSet.size - 1})",
                "remove the tile in Tiled, or add the tileset it came from back to the map",
            )
        }
        return (id and Tile.FLIPS) or (local + 1)
    }
}

/** Rejects a tile or group [layer] with a parallax factor other than 1 or a tint, which klyph does not draw. */
private fun checkLayerFeatures(layer: JsonObject) {
    for (name in listOf("parallaxx", "parallaxy")) {
        val factor = layer.doubleOrNull(name) ?: 1.0
        if (factor != 1.0) {
            layer.fail(
                "has '$name' set to $factor, which klyph does not read",
                "set the layer's parallax factors to 1 and use a ParallaxLayer",
            )
        }
    }
    val tint = layer.stringOrNull("tintcolor").orEmpty()
    if (tint.isNotEmpty()) {
        layer.fail("has the tint colour $tint, which klyph does not read", "remove the layer's tint colour; set TileLayer.color instead")
    }
}

/** Rejects a [tileset] whose Drawing Offset is not 0, 0, which would move its tiles off their cells. */
private fun checkTileOffset(tileset: JsonObject) {
    val offset = tileset.valueOrNull("tileoffset") as? Map<*, *> ?: return
    val x = offset["x"] as? Double ?: 0.0
    val y = offset["y"] as? Double ?: 0.0
    if (x != 0.0 || y != 0.0) {
        tileset.fail(
            "has a tile offset of $x, $y, which klyph does not read",
            "set the Drawing Offset to 0, 0 in Tiled's Tileset Properties",
        )
    }
}

private const val MAP_FIX = "save the map again from Tiled as JSON (.tmj)"

private const val CSV_FIX = "export the map with Tile Layer Format CSV in Tiled's Map Properties"

/** The largest Tiled tile id, flags included, an unsigned 32-bit number. */
private const val UINT_MAX = 4294967295.0

/** Returns `true` if [value] is a whole number from 0 to [UINT_MAX], which every Tiled tile id is. */
private fun isTiledId(value: Double): Boolean = value >= 0.0 && value <= UINT_MAX && value == kotlin.math.floor(value)

private fun csvIds(layer: JsonObject, count: Int): IntArray {
    val items = layer.valueOrNull("data") as? List<*>
        ?: layer.fail("has no 'data' array of tile ids", CSV_FIX)
    if (items.size != count) layer.fail("has ${items.size} tile ids for $count cells", "save the map again from Tiled")
    return IntArray(count) { index ->
        val value = items[index] as? Double
        if (value == null || !isTiledId(value)) {
            layer.fail("has $value as tile id $index, which is not a Tiled tile id", "save the map again from Tiled")
        }
        value.toLong().toInt()
    }
}

private fun base64Ids(layer: JsonObject, count: Int): IntArray {
    val text = layer.valueOrNull("data") as? String
        ?: layer.fail("has no 'data' string of base64 tile ids", CSV_FIX)
    val bytes = try {
        Base64.decode(text.filterNot { it.isWhitespace() })
    } catch (e: IllegalArgumentException) {
        throw TileMapException("${layer.context} has data that is not valid base64; save the map again from Tiled.", e)
    }
    if (bytes.size != count * 4) {
        layer.fail("has ${bytes.size} bytes of tile ids for $count cells, not ${count * 4}", "save the map again from Tiled")
    }
    return IntArray(count) { index ->
        val at = index * 4
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
    }
}

private fun positive(json: JsonObject, name: String): Int {
    val value = json.int(name)
    if (value <= 0) json.fail("has '$name' set to $value, which must be positive", "save the file again from Tiled")
    return value
}

/** Parses [text], the file shown as [display], as one JSON object whose messages end with [fix]. */
private fun parseObject(text: String, display: String, fix: String): JsonObject {
    val value = try {
        parseJson(text)
    } catch (e: JsonException) {
        val problem = e.message.orEmpty().removeSuffix(".").replaceFirstChar { it.lowercase() }
        throw TileMapException("$display is not valid JSON: $problem; $fix.", e)
    }
    @Suppress("UNCHECKED_CAST")
    val members = value as? Map<String, Any?> ?: throw TileMapException("$display does not hold a JSON object; $fix.")
    return JsonObject(members, display, fix)
}

/** Returns the value of a `solid` or `oneWay` tile property, which must be a bool property. */
private fun flag(property: JsonObject): Boolean {
    val type = property.stringOrNull("type") ?: "string"
    if (type != "bool") property.fail("is a $type property", "make it a bool property in Tiled's Tile Properties")
    return property.booleanOrNull("value") ?: false
}

/** Fails with [problem] about this object and [fix] as the sentence's end. */
private fun JsonObject.fail(problem: String, fix: String): Nothing = throw TileMapException("$context $problem; $fix.")

/**
 * Returns [relative] resolved against the folder of the file at [file]: [relative] itself when it
 * is absolute, with `../` steps taken out where the folder has a name to remove.
 */
internal fun against(file: String, relative: String): String {
    if (Files.isAbsolute(relative)) return relative
    val folder = file.substring(0, maxOf(file.lastIndexOf('/'), file.lastIndexOf('\\')) + 1)
    var base = folder
    var rest = relative
    while (rest.startsWith("../") && base.isNotEmpty() && !base.endsWith("../")) {
        val trimmed = base.dropLast(1)
        val cut = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        // A folder that is only a root ("/") or a drive has nothing above it to step into.
        if (cut < 0 && trimmed.isEmpty()) break
        base = if (cut < 0) "" else trimmed.substring(0, cut + 1)
        rest = rest.removePrefix("../")
    }
    return base + rest
}
