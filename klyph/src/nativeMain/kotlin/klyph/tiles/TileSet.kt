package klyph.tiles

import klyph.render.SpriteSheet
import klyph.render.TextureRegion

/**
 * The pictures a tile layer draws, cut from one image, and what the game should know about each:
 * [sheet] holds one region per tile id, every tile is [tileWidth] by [tileHeight] world units, and
 * [isSolid] and [isOneWay] say which tiles walls and floors are made of. `App.assets.tileMap` makes
 * one from the map's tileset; build one yourself for a map made in code:
 *
 * ```
 * val sheet = SpriteSheet.fromGrid(assets.texture("tiles.png", filter = SamplerFilter.NEAREST), columns = 4, rows = 1)
 * val tileSet = TileSet(sheet, tileWidth = 16f, tileHeight = 16f, solidIds = setOf(0, 1))
 * ```
 *
 * The tile with id `i` shows `sheet[i]`, so [size] is the number of tiles. [solidIds] are the ids
 * of the tiles `spawnSolids` makes into walls and floors, [oneWayIds] those it makes into ledges a
 * body jumps up through, `Passable.FROM_BELOW`; a tile in both is solid. Tiles touch in the
 * image, so draw tile art with `SamplerFilter.NEAREST`, or give the image a spacing between tiles,
 * or neighbors bleed into each other's edges when the camera zooms or moves by part of a pixel.
 *
 * Has no sample because a [SpriteSheet] needs a texture, which needs a GPU device.
 *
 * @throws IllegalArgumentException if [tileWidth] or [tileHeight] is not positive and finite, or
 *   an id in [solidIds] or [oneWayIds] is not in `0 until sheet.size`.
 */
public class TileSet(
    /** The pictures of the tiles, one region per tile id, all on one texture. */
    public val sheet: SpriteSheet,
    /** Width of one tile in world units, before a layer's scale. */
    public val tileWidth: Float,
    /** Height of one tile in world units, before a layer's scale. */
    public val tileHeight: Float,
    solidIds: Set<Int> = emptySet(),
    oneWayIds: Set<Int> = emptySet(),
) {
    init {
        require(tileWidth > 0f && tileWidth.isFinite() && tileHeight > 0f && tileHeight.isFinite()) {
            "Tile size ${tileWidth}x$tileHeight is not positive and finite."
        }
    }

    /** Ids of the tiles that make walls and floors, solid from every side. */
    public val solidIds: Set<Int> = checkedIds(solidIds, "Solid")

    /** Ids of the tiles that make one-way ledges, which a body jumps up through and lands on. */
    public val oneWayIds: Set<Int> = checkedIds(oneWayIds, "One-way")

    private val solid = BooleanArray(sheet.size).also { array -> this.solidIds.forEach { array[it] = true } }
    private val oneWay = BooleanArray(sheet.size).also { array -> this.oneWayIds.forEach { array[it] = true } }

    /** Number of tiles, the ids from 0 up to it; the size of [sheet]. */
    public val size: Int get() = sheet.size

    /** Returns `true` if [tile] is not empty and its id is one of the [solidIds]. Flips do not matter. */
    public fun isSolid(tile: Tile): Boolean {
        val id = tile.id
        return id >= 0 && id < solid.size && solid[id]
    }

    /**
     * The opposite of [isSolid], made once, so the searches of `Pathfinding.kt` default to it without
     * making a new lambda on every call.
     */
    internal val isNotSolid: (Tile) -> Boolean = { !isSolid(it) }

    /** Returns `true` if [tile] is not empty and its id is one of the [oneWayIds]. Flips do not matter. */
    public fun isOneWay(tile: Tile): Boolean {
        val id = tile.id
        return id >= 0 && id < oneWay.size && oneWay[id]
    }

    private fun checkedIds(ids: Set<Int>, kind: String): Set<Int> {
        for (id in ids) {
            require(id in 0 until sheet.size) { "$kind tile id $id is not between 0 and ${sheet.size - 1}, the ids of the sheet's tiles." }
        }
        return ids.toSet()
    }

    /** Returns `TileSet(size=48, tile=16.0x16.0, solid=12, oneWay=3, texture=Texture(128x96, NEAREST))`. The format may change. */
    override fun toString(): String =
        "TileSet(size=$size, tile=${tileWidth}x$tileHeight, solid=${solidIds.size}, oneWay=${oneWayIds.size}, texture=${sheet.texture})"
}

/**
 * Returns the picture of [tile], the region of [TileSet.sheet] at its id, without its flips.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 *
 * @throws IllegalArgumentException if [tile] is [Tile.EMPTY].
 * @throws IndexOutOfBoundsException if the tile's id is not in `0 until size`.
 */
public fun TileSet.regionOf(tile: Tile): TextureRegion {
    require(!tile.isEmpty) { "Tile.EMPTY has no picture." }
    return sheet[tile.id]
}
