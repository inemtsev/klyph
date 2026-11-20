package klyph.tiles

import klyph.KlyphException

/**
 * Thrown when a map file cannot be turned into a [TileMap]: it is not valid JSON, a field Tiled
 * always writes is missing, or the map uses a feature klyph does not read, such as compressed
 * layers, an infinite map or several tilesets. The message names the file, the layer or tileset
 * when there is one, and what to change in Tiled.
 */
public class TileMapException(message: String, cause: Throwable? = null) : KlyphException(message, cause)
