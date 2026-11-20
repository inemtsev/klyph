// Snippets of docs/tilemaps.md. They compile with the samples but never run, because they need
// a running app. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Shape
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.platform.MouseButton
import klyph.platform.SamplerFilter
import klyph.render.SpriteSheet
import klyph.render.fromGrid
import klyph.tiles.Tile
import klyph.tiles.TileLayer
import klyph.tiles.TileSet
import klyph.tiles.get
import klyph.tiles.solidRectangles
import klyph.tiles.spawnSolids
import klyph.tiles.spawnTileLayer
import klyph.tiles.tileAt

class Tilemaps {
    fun nearest() {
        // snippet: nearest
        runApp(AppConfig(title = "My platformer", textureFilter = SamplerFilter.NEAREST)) {
            // Every texture, sprite sheet and tileset image now loads with sharp pixels.
        }
        // end snippet
    }

    fun App.loadLevel() {
        // snippet: load
        val map = assets.tileMap("level.tmj")
        // Layers spawned into the same sprite layer draw in the order they are spawned: the sky first.
        commands.spawnTileLayer(map["sky"])
        commands.spawnTileLayer(map["ground"])
        // Walls and floors from the tiles marked solid, ledges from the ones marked oneWay.
        commands.spawnSolids(map["ground"])
        // end snippet
    }

    fun App.placeLevel() {
        // snippet: place
        val map = assets.tileMap("level.tmj")
        // The second room starts where the first one ends, 40 tiles of 16 units to the right.
        commands.spawnTileLayer(map["ground"], x = 640f, y = 0f, layer = 1)
        commands.spawnSolids(map["ground"], x = 640f, y = 0f)
        // end snippet
    }

    fun App.spikes(hero: Entity) {
        // snippet: query
        val map = assets.tileMap("level.tmj")
        val ground = map["ground"]
        // The id Tiled shows for the spike tile in its Tile Properties panel.
        val spikes = 7
        addSystem(Phase.FIXED_UPDATE) { app ->
            val feet = app.world.get(hero, Transform)
            // The layer was spawned at (0, 0), so world positions are layer positions too.
            val tile = ground.tileAt(feet.x, feet.y + 1f)
            // Back to the start of the level.
            if (tile.id == spikes) feet.x = 32f
        }
        // end snippet
    }

    fun App.breakBlocks(level: Entity) {
        // snippet: change
        addSystem(Phase.UPDATE) { app ->
            val tiles = app.world.get(level, TileLayer)
            val mouse = app.input.mouse.worldPosition
            // The layer is at (0, 0) at scale 1, so a world position divided by the tile size is a cell.
            val column = (mouse.x / tiles.tileSet.tileWidth).toInt()
            val row = (mouse.y / tiles.tileSet.tileHeight).toInt()
            val clicked = app.input.mouse.isJustPressed(MouseButton.LEFT)
            // Clicked on a brick, tile 3: it is gone from the next frame on.
            if (clicked && tiles.tileAt(mouse.x, mouse.y).id == 3) tiles[column, row] = Tile.EMPTY
        }
        // end snippet
    }

    fun App.waterAreas() {
        // snippet: own-colliders
        val water = assets.tileMap("level.tmj")["water"]
        val waterLayer = CollisionLayers.of(3)
        // Every cell with any tile in it counts, merged into as few boxes as possible.
        for (area in water.solidRectangles { !it.isEmpty }) {
            commands.spawn {
                add(Transform, Transform(area.x + area.width / 2, area.y + area.height / 2))
                add(Collider, Collider(Shape.Box(area.width, area.height), layers = waterLayer))
            }
        }
        // end snippet
    }

    fun App.mapInCode() {
        // snippet: in-code
        val sheet = SpriteSheet.fromGrid(assets.texture("tiles.png"), columns = 4, rows = 1)
        // Tile 0 is a brick, solid; tile 2 a plank you can jump up through.
        val tileSet = TileSet(sheet, tileWidth = 16f, tileHeight = 16f, solidIds = setOf(0), oneWayIds = setOf(2))
        val floor = TileLayer(tileSet, columns = 30, rows = 4)
        for (column in 0 until 30) floor[column, 3] = Tile.of(0)
        for (column in 10 until 15) floor[column, 1] = Tile.of(2)
        commands.spawn {
            add(Transform, Transform(x = 0f, y = 300f))
            add(TileLayer, floor)
        }
        commands.spawnSolids(floor, x = 0f, y = 300f)
        // end snippet
    }
}
