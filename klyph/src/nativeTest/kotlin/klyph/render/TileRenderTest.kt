package klyph.render

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.tiles.Tile
import klyph.tiles.TileLayer
import klyph.tiles.TileSet
import klyph.tiles.flippedDiagonally
import klyph.tiles.flippedX
import klyph.tiles.flippedY
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TileRenderTest {
    private val texture = Texture(32, 8)

    /** Four 8 by 8 tiles drawn 16 by 16 world units. */
    private val tileSet = TileSet(SpriteSheet.fromGrid(texture, columns = 4, rows = 1), tileWidth = 16f, tileHeight = 16f)

    private fun camera(x: Float, y: Float): Camera = Camera(x = x, y = y, viewportWidth = 800f, viewportHeight = 600f)

    /** Adds an entity with a full [columns] by [rows] layer of tile 1 at ([x], [y]) and returns it. */
    private fun World.fullLayer(columns: Int, rows: Int, x: Float = 0f, y: Float = 0f, scale: Float = 1f): Entity {
        val layer = TileLayer(tileSet, columns, rows)
        for (row in 0 until rows) for (column in 0 until columns) layer[column, row] = Tile.of(1)
        val entity = create()
        add(entity, Transform, Transform(x, y, scaleX = scale, scaleY = scale))
        add(entity, TileLayer, layer)
        return entity
    }

    /** Returns the records the sprite pass writes for the tile layers of [this] seen through [camera]. */
    private fun World.tileRecords(camera: Camera): List<List<Float>> {
        val plan = TilePlan()
        plan.update(this, camera, 1f)
        val records = ArrayList<List<Float>>()
        for (slot in 0 until plan.layerCount) {
            plan.begin(slot)
            while (plan.next()) records += plan.template.toList()
        }
        return records
    }

    @Test
    fun onlyTheCellsInViewAreWritten() {
        val world = World()
        world.fullLayer(columns = 100, rows = 100)
        // The view covers x 0 to 800 and y 0 to 600: 51 columns (the 51st starts at the right edge) and 38 rows.
        assertEquals(51 * 38, world.tileRecords(camera(400f, 300f)).size)
        // x 600 to 1400 is columns 37 to 87, y 700 to 1300 rows 43 to 81.
        val records = world.tileRecords(camera(1000f, 1000f))
        assertEquals(51 * 39, records.size)
        for (record in records) {
            assertTrue(record[0] + 8f >= 600f && record[0] - 8f <= 1400f && record[1] + 8f >= 700f && record[1] - 8f <= 1300f, "$record")
        }
    }

    @Test
    fun aViewThatSeesPartOfTheLayerCountsOnlyThatPart() {
        val world = World()
        world.fullLayer(columns = 10, rows = 10, x = 700f, y = 500f)
        // The view ends at x 800 and y 600: columns 0 to 6 and rows 0 to 6 of the layer reach into it.
        val plan = TilePlan()
        plan.update(world, camera(400f, 300f), 1f)
        assertEquals(1, plan.layerCount)
        assertEquals(49L, plan.cellsInView)
        assertEquals(49, world.tileRecords(camera(400f, 300f)).size)
        assertEquals(0, world.tileRecords(camera(-2000f, 300f)).size)
    }

    @Test
    fun emptyCellsAndHiddenLayersWriteNothing() {
        val world = World()
        val layer = TileLayer(tileSet, columns = 4, rows = 4)
        layer[1, 2] = Tile.of(3)
        val entity = world.create()
        world.add(entity, Transform, Transform(100f, 100f))
        world.add(entity, TileLayer, layer)
        val records = world.tileRecords(camera(400f, 300f))
        assertEquals(1, records.size)
        // The middle of cell (1, 2): 100 + 1.5 * 16 and 100 + 2.5 * 16.
        assertEquals(listOf(124f, 140f, 0f, 0f, 16f, 16f, 0.5f, 0.5f, 0.75f, 0f, 1f, 1f), records[0].take(12))
        layer.isVisible = false
        assertEquals(0, world.tileRecords(camera(400f, 300f)).size)
    }

    @Test
    fun scaleGrowsTheCellsAndColorTintsThem() {
        val world = World()
        val entity = world.fullLayer(columns = 2, rows = 1, x = 10f, y = 20f, scale = 2f)
        val tint = Color(red = 1f, green = 0.5f, blue = 0.25f, alpha = 0.5f)
        world.get(entity, TileLayer).color = tint
        val records = world.tileRecords(camera(400f, 300f))
        assertEquals(listOf(26f, 36f), records[0].take(2))
        assertEquals(listOf(58f, 36f), records[1].take(2))
        assertEquals(listOf(32f, 32f), records[1].subList(4, 6))
        assertEquals(listOf(tint.red, tint.green, tint.blue, tint.alpha), records[1].subList(12, 16))
    }

    @Test
    fun layersThatCannotBeDrawnNameTheirEntity() {
        val world = World()
        val entity = world.fullLayer(columns = 2, rows = 2)
        world.get(entity, Transform).rotation = 0.5f
        val rotated = assertFailsWith<IllegalStateException> { world.tileRecords(camera(400f, 300f)) }
        assertTrue("$entity" in rotated.message.orEmpty(), rotated.message)
        world.get(entity, Transform).rotation = 0f
        world.get(entity, Transform).scaleX = -1f
        assertFailsWith<IllegalStateException> { world.tileRecords(camera(400f, 300f)) }
        world.get(entity, Transform).scaleX = 1f
        val parent = world.create()
        world.add(parent, Transform, Transform())
        world.setParent(entity, parent)
        val child = assertFailsWith<IllegalStateException> { world.tileRecords(camera(400f, 300f)) }
        assertTrue("parent" in child.message.orEmpty(), child.message)
    }

    /**
     * Checks every combination of flips against Tiled's definition: the diagonal flip swaps x and y
     * first, then the x and y flips mirror. A point at fractions (px, py) of the cell must show the
     * point of the tile's picture that the definition maps it to.
     */
    @Test
    fun flipsDrawAsTiledDefinesThem() {
        val base = Tile.of(1)
        for (bits in 0 until 8) {
            var tile = base
            if (bits and 1 != 0) tile = tile.flippedX()
            if (bits and 2 != 0) tile = tile.flippedY()
            if (bits and 4 != 0) tile = tile.flippedDiagonally()
            val world = World()
            val layer = TileLayer(tileSet, columns = 1, rows = 1)
            layer[0, 0] = tile
            val entity = world.create()
            world.add(entity, Transform, Transform(32f, 48f))
            world.add(entity, TileLayer, layer)
            val quad = ShaderQuad(world.tileRecords(camera(400f, 300f)).single())
            val region = tileSet.sheet[1]
            for ((ux, uy) in listOf(0.2f to 0.1f, 0.8f to 0.3f, 0.4f to 0.9f, 0f to 0f, 1f to 1f)) {
                val position = quad.positionOf(ux, uy)
                val px = (position[0] - 32f) / 16f
                val py = (position[1] - 48f) / 16f
                val texel = quad.textureCoordinatesOf(ux, uy)
                val sx = (texel[0] - region.u0) / (region.u1 - region.u0)
                val sy = (texel[1] - region.v0) / (region.v1 - region.v0)
                val fx = if (tile.isFlippedX) 1f - px else px
                val fy = if (tile.isFlippedY) 1f - py else py
                val expected = if (tile.isFlippedDiagonally) listOf(fy, fx) else listOf(fx, fy)
                assertTrue(
                    abs(expected[0] - sx) < 1e-4f && abs(expected[1] - sy) < 1e-4f && px in -1e-4f..1.0001f && py in -1e-4f..1.0001f,
                    "$tile at quad point ($ux, $uy): cell point ($px, $py) shows ($sx, $sy), expected $expected.",
                )
            }
        }
    }

    @Test
    fun aDiagonalFlipFillsACellThatIsNotSquare() {
        val sheet = SpriteSheet.fromGrid(Texture(16, 8), columns = 1, rows = 1)
        val wide = TileSet(sheet, tileWidth = 32f, tileHeight = 16f)
        val world = World()
        val layer = TileLayer(wide, columns = 1, rows = 1)
        layer[0, 0] = Tile.of(0).flippedDiagonally()
        val entity = world.create()
        world.add(entity, Transform, Transform(0f, 0f))
        world.add(entity, TileLayer, layer)
        val corners = ShaderQuad(world.tileRecords(camera(400f, 300f)).single()).corners
        val xs = corners.filterIndexed { index, _ -> index % 2 == 0 }
        val ys = corners.filterIndexed { index, _ -> index % 2 == 1 }
        assertTrue(abs(xs.min()) < 1e-3f && abs(xs.max() - 32f) < 1e-3f, "$corners")
        assertTrue(abs(ys.min()) < 1e-3f && abs(ys.max() - 16f) < 1e-3f, "$corners")
    }
}
