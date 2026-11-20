package klyph.tiles

import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Passable
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.entity.Commands
import klyph.entity.Transform
import klyph.entity.World
import klyph.math.Rect
import klyph.render.SpriteSheet
import klyph.render.Texture
import klyph.render.fromGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class TileLayerTest {
    /** Four 8 by 8 tiles drawn 16 by 16 world units: 0 and 1 solid, 2 one-way, 3 decoration. */
    private val tileSet = TileSet(
        SpriteSheet.fromGrid(Texture(32, 8), columns = 4, rows = 1),
        tileWidth = 16f,
        tileHeight = 16f,
        solidIds = setOf(0, 1),
        oneWayIds = setOf(2),
    )

    /** Returns a layer whose cells are the characters of [rows]: '#' tile 0, 'B' tile 1, '=' tile 2, '~' tile 3, '.' empty. */
    private fun layerOf(vararg rows: String): TileLayer {
        val layer = TileLayer(tileSet, rows[0].length, rows.size)
        for ((row, line) in rows.withIndex()) {
            for ((column, c) in line.withIndex()) {
                val id = "#B=~".indexOf(c)
                if (id >= 0) layer[column, row] = Tile.of(id)
            }
        }
        return layer
    }

    @Test
    fun getReturnsWhatSetPut() {
        val layer = TileLayer(tileSet, columns = 3, rows = 2)
        assertEquals(Tile.EMPTY, layer[2, 1])
        layer[2, 1] = Tile.of(3).flippedX()
        assertEquals(Tile.of(3).flippedX(), layer[2, 1])
        layer[2, 1] = Tile.EMPTY
        assertEquals(Tile.EMPTY, layer[2, 1])
        assertEquals(
            "TileLayer(size=3x2, tiles=0, layer=0, color=${klyph.platform.Color.WHITE}, visible=true, tileSet=$tileSet)",
            layer.toString(),
        )
    }

    @Test
    fun getAndSetRejectCellsOutside() {
        val layer = TileLayer(tileSet, columns = 3, rows = 2)
        assertEquals(
            "Cell (3, 0) is outside the tile layer of 3x2 cells.",
            assertFailsWith<IndexOutOfBoundsException> { layer[3, 0] }.message,
        )
        assertFailsWith<IndexOutOfBoundsException> { layer[0, -1] = Tile.of(0) }
    }

    @Test
    fun setRejectsIdsTheTileSetDoesNotHave() {
        val layer = TileLayer(tileSet, columns = 1, rows = 1)
        assertEquals(
            "Tile id 4 is not between 0 and 3, the ids of the tile set.",
            assertFailsWith<IllegalArgumentException> { layer[0, 0] = Tile.of(4) }.message,
        )
    }

    @Test
    fun constructorRejectsEmptyAndHugeLayers() {
        assertFailsWith<IllegalArgumentException> { TileLayer(tileSet, columns = 0, rows = 4) }
        assertFailsWith<IllegalArgumentException> { TileLayer(tileSet, columns = 5000, rows = 5000) }
    }

    @Test
    fun tileAtFindsTheCellUnderAPoint() {
        val layer = layerOf(
            "#.",
            ".B",
        )
        assertEquals(Tile.of(0), layer.tileAt(0f, 0f))
        assertEquals(Tile.of(0), layer.tileAt(15.9f, 15.9f))
        assertEquals(Tile.EMPTY, layer.tileAt(16f, 0f), "An edge belongs to the cell right of it.")
        assertEquals(Tile.of(1), layer.tileAt(16f, 16f))
        assertEquals(Tile.of(1), layer.tileAt(31.9f, 31.9f))
    }

    @Test
    fun tileAtIsEmptyOutsideTheLayer() {
        val layer = layerOf("##", "##")
        for ((x, y) in listOf(-0.1f to 0f, 0f to -0.1f, 32f to 0f, 0f to 32f, Float.NaN to 0f, Float.POSITIVE_INFINITY to 0f)) {
            assertEquals(Tile.EMPTY, layer.tileAt(x, y), "At $x, $y.")
        }
    }

    @Test
    fun solidRectanglesMergeAnLShape() {
        val layer = layerOf(
            "#..",
            "B..",
            "###",
        )
        assertEquals(listOf(Rect(0f, 0f, 16f, 32f), Rect(0f, 32f, 48f, 16f)), layer.solidRectangles())
    }

    @Test
    fun solidRectanglesGoAroundAHole() {
        val layer = layerOf(
            "###",
            "#.#",
            "###",
        )
        assertEquals(
            listOf(Rect(0f, 0f, 48f, 16f), Rect(0f, 16f, 16f, 16f), Rect(32f, 16f, 16f, 16f), Rect(0f, 32f, 48f, 16f)),
            layer.solidRectangles(),
        )
    }

    @Test
    fun solidRectanglesMergeEqualRunsDownwards() {
        val layer = layerOf(
            ".##.",
            ".##.",
            "####",
            "....",
            "##..",
        )
        assertEquals(
            listOf(Rect(16f, 0f, 32f, 32f), Rect(0f, 32f, 64f, 16f), Rect(0f, 64f, 32f, 16f)),
            layer.solidRectangles(),
        )
    }

    @Test
    fun solidRectanglesKeepOneWayRunsApart() {
        val layer = layerOf(
            "~==~##",
            "......",
        )
        assertEquals(listOf(Rect(64f, 0f, 32f, 16f)), layer.solidRectangles())
        assertEquals(listOf(Rect(16f, 0f, 32f, 16f)), layer.solidRectangles(tileSet::isOneWay))
        assertEquals(emptyList(), TileLayer(tileSet, 2, 2).solidRectangles())
    }

    @Test
    fun tileCountTracksSetToAndFromEmpty() {
        val layer = TileLayer(tileSet, 2, 2)
        assertEquals(0, layer.tileCount)
        layer[0, 0] = Tile.of(1)
        layer[1, 1] = Tile.of(2)
        assertEquals(2, layer.tileCount)
        layer[0, 0] = Tile.of(3)
        assertEquals(2, layer.tileCount, "Replacing a tile keeps the count.")
        layer[0, 0] = Tile.EMPTY
        layer[0, 1] = Tile.EMPTY
        assertEquals(1, layer.tileCount)
        assertEquals(3, layerOf("#=", "~.").toTileLayer().tileCount)
    }

    @Test
    fun toTileLayerCopiesTheCells() {
        val layer = layerOf("#=", "~.")
        val copy = layer.toTileLayer()
        assertNotSame(layer, copy)
        for (row in 0 until 2) for (column in 0 until 2) assertEquals(layer[column, row], copy[column, row])
        copy[0, 0] = Tile.EMPTY
        assertEquals(Tile.of(0), layer[0, 0], "The copy is independent.")
    }

    @Test
    fun spawnSolidsMakesSolidsAndOneWayLedges() {
        val layer = layerOf(
            "==..",
            "####",
        )
        val world = World()
        val commands = Commands(world)
        val entities = commands.spawnSolids(layer, x = 100f, y = 50f, layers = CollisionLayers.of(2))
        commands.apply()
        assertEquals(2, entities.size)
        val (floor, ledge) = entities
        assertEquals(Passable.NEVER, world.get(floor, Solid).passable)
        assertEquals(132f, world.get(floor, Transform).x)
        assertEquals(74f, world.get(floor, Transform).y)
        val box = world.get(floor, Collider).shape as Shape.Box
        assertEquals(64f, box.width)
        assertEquals(16f, box.height)
        assertEquals(CollisionLayers.of(2), world.get(floor, Collider).layers)
        assertEquals(Passable.FROM_BELOW, world.get(ledge, Solid).passable)
        assertEquals(116f, world.get(ledge, Transform).x)
        assertEquals(58f, world.get(ledge, Transform).y)
    }

    @Test
    fun mapLayerOffsetsMoveSpawnedLayerAndSolids() {
        val layer = TileMapLayer("ground", tileSet, 2, 1, intArrayOf(Tile.of(0).bits, 0), true, 1f, offsetX = 5f, offsetY = -3f)
        val world = World()
        val commands = Commands(world)
        val picture = commands.spawnTileLayer(layer, x = 100f, y = 50f)
        val solids = commands.spawnSolids(layer, x = 100f, y = 50f)
        commands.apply()
        assertEquals(105f, world.get(picture, Transform).x)
        assertEquals(47f, world.get(picture, Transform).y)
        // The tile is 16 by 16 at the layer's corner, so its center lies 8 units in from it.
        assertEquals(113f, world.get(solids[0], Transform).x)
        assertEquals(55f, world.get(solids[0], Transform).y)
        assertEquals("TileMapLayer('ground', size=2x1, tiles=1, offset=5.0x-3.0)", layer.toString())
    }

    @Test
    fun tileSetChecksItsIds() {
        val sheet = SpriteSheet.fromGrid(Texture(32, 8), columns = 4, rows = 1)
        assertFailsWith<IllegalArgumentException> { TileSet(sheet, 8f, 8f, solidIds = setOf(4)) }
        assertFailsWith<IllegalArgumentException> { TileSet(sheet, 8f, 8f, oneWayIds = setOf(-1)) }
        assertFailsWith<IllegalArgumentException> { TileSet(sheet, 0f, 8f) }
        val set = TileSet(sheet, 8f, 8f, solidIds = setOf(1), oneWayIds = setOf(1, 2))
        assertEquals(true, set.isSolid(Tile.of(1).flippedX()))
        assertEquals(false, set.isSolid(Tile.EMPTY))
        assertEquals(true, set.isOneWay(Tile.of(2)))
        assertEquals(sheet[3], set.regionOf(Tile.of(3).flippedY()))
        assertFailsWith<IllegalArgumentException> { set.regionOf(Tile.EMPTY) }
    }
}
