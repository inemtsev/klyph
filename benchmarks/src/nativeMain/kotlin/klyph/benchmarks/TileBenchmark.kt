package klyph.benchmarks

import klyph.app.Phase
import klyph.app.runApp
import klyph.entity.Transform
import klyph.platform.Color
import klyph.platform.Image
import klyph.platform.SamplerFilter
import klyph.platform.fillRect
import klyph.render.SpriteSheet
import klyph.render.fromGrid
import klyph.tiles.Tile
import klyph.tiles.TileLayer
import klyph.tiles.TileSet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws [TILE_LAYERS] tile layers of 16-point tiles, each far larger than the window and filled
 * with random tiles of one 4-tile sheet, through a camera that circles over them, so every frame
 * draws eight full screens of tiles: about 3,700 cells a layer in a 1280 by 720 point window. Each
 * layer is in its own sprite layer, one draw call each. The report's `sprites` count is every
 * quad drawn, the cells in view of all layers.
 */
internal val tileBenchmark: Benchmark = Benchmark(
    name = "tiles",
    description = "Eight full-screen layers of 16-point tiles under a moving camera; measures the tile pass of the sprite renderer.",
) { settings ->
    var sprites = 0
    val report = StatsReport(settings.frameLimit) { "sprites=$sprites layers=$TILE_LAYERS" }
    runApp(settings.config) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            val image = Image(64, 16)
            for (k in 0 until 4) image.fillRect(k * 16, 0, 16, 16, TILE_COLORS[k])
            val texture = app.assets.createTexture(image, SamplerFilter.NEAREST)
            val tileSet = TileSet(SpriteSheet.fromGrid(texture, columns = 4, rows = 1), tileWidth = TILE_SIZE, tileHeight = TILE_SIZE)
            val random = Random(42)
            repeat(TILE_LAYERS) { index ->
                val layer = TileLayer(tileSet, LAYER_COLUMNS, LAYER_ROWS, layer = index)
                for (row in 0 until LAYER_ROWS) {
                    for (column in 0 until LAYER_COLUMNS) layer[column, row] = Tile.of(random.nextInt(4))
                }
                // Lower layers show through the transparent upper ones.
                layer.color = Color(red = 1f, green = 1f, blue = 1f, alpha = if (index == 0) 1f else 0.3f)
                val entity = app.world.create()
                app.world.add(entity, Transform, Transform(0f, 0f))
                app.world.add(entity, TileLayer, layer)
            }
            println("layers=$TILE_LAYERS size=${LAYER_COLUMNS}x$LAYER_ROWS tile=$TILE_SIZE")
        }
        addSystem(Phase.UPDATE) { app ->
            sprites = app.stats.spriteCount
            // A circle of 1000 points around the middle of the layers, once every 10 seconds.
            val angle = app.time.frameCount * 0.0105f
            app.camera.x = LAYER_COLUMNS * TILE_SIZE * 0.5f + 1000f * cos(angle)
            app.camera.y = LAYER_ROWS * TILE_SIZE * 0.5f + 1000f * sin(angle)
        }
    }
}

private const val TILE_LAYERS = 8
private const val TILE_SIZE = 16f
private const val LAYER_COLUMNS = 400
private const val LAYER_ROWS = 300

private val TILE_COLORS: List<Color> = listOf(
    Color(red = 0.7f, green = 0.3f, blue = 0.2f),
    Color(red = 0.25f, green = 0.7f, blue = 0.25f),
    Color(red = 0.3f, green = 0.4f, blue = 0.8f),
    Color(red = 0.9f, green = 0.8f, blue = 0.3f),
)
