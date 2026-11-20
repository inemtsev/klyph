package klyph.tiles

import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Passable
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.entity.Commands
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.platform.Color
import klyph.platform.withAlpha

/**
 * Queues spawning an entity that draws [source], a layer of a map, with its top-left corner at
 * ([x], [y]) plus the layer's [TileMapLayer.offsetX] and [TileMapLayer.offsetY] in world units,
 * and returns the new entity, as [Commands.spawn] does. Write `commands.spawnTileLayer(map["ground"])`.
 *
 * The entity gets a `Transform` at that place, where Tiled shows the layer, and a [TileLayer]
 * holding a copy of the layer's cells, drawn in sprite [layer], which a game may change through `world.get(entity, TileLayer)`.
 * The copy takes the layer's visibility from Tiled and its opacity as the alpha of its color.
 * Layers spawned into the same sprite [layer] draw in the order they were spawned, so spawn a
 * map's layers in the order of [TileMap.layers], or give each its own [layer].
 *
 * Has no sample because reading a map needs its tileset image on a GPU device.
 */
public fun Commands.spawnTileLayer(source: TileMapLayer, x: Float = 0f, y: Float = 0f, layer: Int = 0): Entity {
    val tiles = source.toTileLayer()
    tiles.layer = layer
    tiles.isVisible = source.isVisible
    tiles.color = Color.WHITE.withAlpha(source.opacity.coerceIn(0f, 1f))
    return spawn {
        add(Transform, Transform(x + source.offsetX, y + source.offsetY))
        add(TileLayer, tiles)
    }
}

/**
 * Queues spawning the walls, floors and ledges of [tiles], a map layer or a [TileLayer], placed
 * as if its top-left corner were at ([x], [y]) in world units, moved by the layer's
 * [TileMapLayer.offsetX] and [TileMapLayer.offsetY] when [tiles] is a map layer, and returns the
 * new entities. Write `commands.spawnSolids(map["ground"])` next to the `spawnTileLayer` call for the same layer and
 * position, and a `KinematicBody` stands on the ground and stops at the walls.
 *
 * Each rectangle of [solidRectangles] for the tiles of [TileSet.solidIds] becomes an entity with a
 * `Transform` at its center, a box `Collider` of its size on [layers] and a [Solid]. Each
 * rectangle of the tiles of [TileSet.oneWayIds] that are not solid becomes the same with
 * `Solid(Passable.FROM_BELOW)`, a ledge a body jumps up through and lands on. Neighboring tiles
 * merge into one rectangle, so a floor is one solid and a body slides along it without catching
 * on the seams between tiles.
 *
 * The solids are made from the cells as they are now, at scale 1: they do not follow later
 * changes to a [TileLayer] or a scale on its entity. To open a hole, destroy the solids and spawn
 * them again from the changed layer.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 */
public fun Commands.spawnSolids(
    tiles: TileGrid,
    x: Float = 0f,
    y: Float = 0f,
    layers: CollisionLayers = CollisionLayers.DEFAULT,
): List<Entity> {
    val tileSet = tiles.tileSet
    val layer = tiles as? TileMapLayer
    val left = x + (layer?.offsetX ?: 0f)
    val top = y + (layer?.offsetY ?: 0f)
    val solid = tiles.solidRectangles(tileSet::isSolid)
    val oneWay = tiles.solidRectangles { tileSet.isOneWay(it) && !tileSet.isSolid(it) }
    val entities = ArrayList<Entity>(solid.size + oneWay.size)
    for (rect in solid) entities += spawnSolid(rect, left, top, layers, Passable.NEVER)
    for (rect in oneWay) entities += spawnSolid(rect, left, top, layers, Passable.FROM_BELOW)
    return entities
}

private fun Commands.spawnSolid(rect: klyph.math.Rect, x: Float, y: Float, layers: CollisionLayers, passable: Passable): Entity =
    spawn {
        add(Transform, Transform(x + rect.x + rect.width * 0.5f, y + rect.y + rect.height * 0.5f))
        add(Collider, Collider(Shape.Box(rect.width, rect.height), layers = layers))
        add(Solid, Solid(passable))
    }
