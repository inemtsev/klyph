package klyph.tiles

import klyph.app.Assets
import klyph.platform.FileException
import klyph.platform.Files
import klyph.platform.Image
import klyph.platform.SamplerAddress
import klyph.platform.SamplerFilter
import klyph.platform.save
import klyph.render.Texture
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TiledLoaderTest {
    /** The filters of the textures the assets uploaded after construction, in order. */
    private val uploads = ArrayList<SamplerFilter>()

    /** Returns assets over [ROOT], with the fixtures written, whose uploads make headless textures. */
    private fun assets(): Assets {
        writeFiles()
        val assets = Assets(
            upload = { image, filter, address -> uploads += filter; Texture(image.width, image.height, filter, address) },
            root = ROOT,
            textureFilter = SamplerFilter.NEAREST,
        )
        uploads.clear()
        return assets
    }

    private fun writeFiles() {
        // 4 tiles of 8 by 8 in a row with a margin of 1 and a spacing of 2: 1 + 4 * 8 + 3 * 2 + 1 = 40 wide, 10 high.
        Image(40, 10).save(ROOT + "tiles.png")
        Image(40, 10).save(ROOT + "art/tiles.png")
        for ((name, text) in FILES) Files.writeBytes(ROOT + name, text.encodeToByteArray())
    }

    private fun message(path: String): String =
        assertFailsWith<TileMapException> { assets().tileMap(path) }.message.orEmpty()

    @Test
    fun readsACsvMapWithAnEmbeddedTileset() {
        val map = assets().tileMap("levels/csv.tmj")
        assertEquals(listOf(SamplerFilter.NEAREST), uploads, "The tileset image loads once, with the configured filter.")
        assertEquals(3, map.columns)
        assertEquals(2, map.rows)
        assertEquals(listOf("ground", "decor"), map.layers.map { it.name })
        assertEquals(emptyList(), map.skippedLayers)
        val ground = map["ground"]
        assertEquals(Tile.of(0), ground[0, 0])
        assertEquals(Tile.EMPTY, ground[1, 0])
        assertEquals(Tile.of(3), ground[2, 1])
        assertFalse(map["decor"].isVisible)
        assertEquals(0.5f, map["decor"].opacity)
        val tileSet = map.tileSet
        assertEquals(4, tileSet.size)
        assertEquals(8f, tileSet.tileWidth)
        assertEquals(setOf(0, 1), tileSet.solidIds)
        assertEquals(setOf(2), tileSet.oneWayIds)
        // Margin 1 and spacing 2 put tile 1 at x 1 + 8 + 2 = 11.
        assertEquals(11, tileSet.sheet[1].x)
        assertEquals(1, tileSet.sheet[1].y)
        assertEquals("TileMap(levels/csv.tmj, size=3x2, layers=[ground, decor], skipped=[])", map.toString())
    }

    @Test
    fun cachesTheMapByPathAndFilter() {
        val assets = assets()
        val map = assets.tileMap("levels/csv.tmj")
        assertSame(map, assets.tileMap("levels/csv.tmj"))
        assertSame(map, assets.tileMapOrNull("levels/csv.tmj"))
        assertEquals(1, uploads.size)
        val linear = assets.tileMap("levels/csv.tmj", filter = SamplerFilter.LINEAR)
        assertEquals(SamplerFilter.LINEAR, linear.tileSet.sheet.texture.filter)
        assertEquals(SamplerAddress.CLAMP, linear.tileSet.sheet.texture.address)
    }

    @Test
    fun readsBase64LayersInLittleEndianOrder() {
        val map = assets().tileMap("levels/base64.tmj")
        val layer = map["ground"]
        assertEquals(
            listOf(Tile.of(0), Tile.EMPTY, Tile.of(2), Tile.of(3), Tile.of(1), Tile.EMPTY),
            (0 until 6).map { layer[it % 3, it / 3] },
        )
    }

    @Test
    fun readsAnExternalTilesetAndItsImageRelativeToIt() {
        val map = assets().tileMap("levels/external.tmj")
        assertEquals(setOf(3), map.tileSet.solidIds)
        // firstgid 5: Tiled id 6 is tile 1.
        assertEquals(Tile.of(1), map["ground"][0, 0])
        assertEquals(1, uploads.size)
    }

    @Test
    fun keepsFlipsAndClearsTheHexagonalBit() {
        val layer = assets().tileMap("levels/flips.tmj")["ground"]
        assertEquals(Tile.of(1).flippedX(), layer[0, 0])
        assertEquals(Tile.of(1).flippedY(), layer[1, 0])
        assertEquals(Tile.of(1).flippedDiagonally().flippedX(), layer[2, 0])
        assertEquals(Tile.of(1), layer[0, 1], "Bit 29 is for hexagonal maps and is cleared.")
    }

    @Test
    fun skipsAndListsLayersThatAreNotTileLayers() {
        val map = assets().tileMap("levels/objects.tmj")
        assertEquals(listOf("ground"), map.layers.map { it.name })
        assertEquals(listOf("spawns", "sky"), map.skippedLayers)
        assertEquals(
            "Layer 'spawns' of levels/objects.tmj is not a tile layer, so klyph skipped it; its tile layers are 'ground'.",
            assertFailsWith<NoSuchElementException> { map["spawns"] }.message,
        )
    }

    @Test
    fun readsLayerOffsets() {
        val map = assets().tileMap("levels/offsets.tmj")
        assertEquals(16.5f, map["plain"].offsetX)
        assertEquals(-8f, map["plain"].offsetY)
        assertEquals(0f, map["ground"].offsetX)
        assertEquals(0f, map["ground"].offsetY)
    }

    @Test
    fun readsTileLayersInsideGroupsAndSkipsTheirObjectLayers() {
        val map = assets().tileMap("levels/groups.tmj")
        assertEquals(listOf("outer", "inner"), map.layers.map { it.name })
        assertEquals(listOf("spawns"), map.skippedLayers)
        assertEquals(Tile.of(0), map["inner"][0, 0])
        // The group's offset (10, 20) and the layer's own (6, 0) add up.
        assertEquals(16f, map["inner"].offsetX)
        assertEquals(20f, map["inner"].offsetY)
        assertEquals(10f, map["outer"].offsetX)
        assertEquals(20f, map["outer"].offsetY)
    }

    @Test
    fun groupsHideAndFadeTheTileLayersInsideThem() {
        val map = assets().tileMap("levels/group-looks.tmj")
        assertFalse(map["hidden"].isVisible, "A hidden group hides a visible layer inside it.")
        assertEquals(0.5f, map["hidden"].opacity)
        assertTrue(map["faded"].isVisible)
        // The group's 0.5 and the layer's own 0.5 multiply.
        assertEquals(0.25f, map["faded"].opacity)
        assertTrue(map["plain"].isVisible)
        assertEquals(1f, map["plain"].opacity)
    }

    @Test
    fun rejectsAFirstTileIdThatIsNotPositive() {
        assertEquals(
            "The tileset of levels/firstgid.tmj has 'firstgid' set to 0, which must be positive; save the file again from Tiled.",
            message("levels/firstgid.tmj"),
        )
    }

    @Test
    fun againstStepsUpButNeverAboveARoot() {
        assertEquals("art/tiles.png", against("levels/map.tmj", "../art/tiles.png"))
        assertEquals("../tiles.png", against("map.tmj", "../tiles.png"))
        assertEquals("/../tiles.png", against("/map.tmj", "../tiles.png"))
        assertEquals("C:/../tiles.png", against("C:/map.tmj", "../tiles.png"))
        assertEquals("C:\\../tiles.png", against("C:\\map.tmj", "../tiles.png"))
        assertEquals("C:/tiles.png", against("C:/levels/map.tmj", "../tiles.png"))
        assertEquals("C:/../tiles.png", against("C:/levels/map.tmj", "../../tiles.png"))
    }

    @Test
    fun rejectsAParallaxFactorOtherThanOne() {
        assertEquals(
            "Layer 'ground' of levels/parallax.tmj has 'parallaxx' set to 0.5, which klyph does not read; " +
                "set the layer's parallax factors to 1 and use a ParallaxLayer.",
            message("levels/parallax.tmj"),
        )
    }

    @Test
    fun rejectsATintColor() {
        assertEquals(
            "Layer 'ground' of levels/tint.tmj has the tint colour #ff0000, which klyph does not read; " +
                "remove the layer's tint colour; set TileLayer.color instead.",
            message("levels/tint.tmj"),
        )
    }

    @Test
    fun rejectsATilesetDrawingOffset() {
        assertEquals(
            "The tileset of levels/tileoffset.tmj has a tile offset of 4.0, 0.0, which klyph does not read; " +
                "set the Drawing Offset to 0, 0 in Tiled's Tileset Properties.",
            message("levels/tileoffset.tmj"),
        )
    }

    @Test
    fun getNamesTheLayersAMapHas() {
        val map = assets().tileMap("levels/csv.tmj")
        assertEquals(
            "levels/csv.tmj has no tile layer called 'grond'; its tile layers are 'ground', 'decor'.",
            assertFailsWith<NoSuchElementException> { map["grond"] }.message,
        )
        assertNull(map.layerOrNull("grond"))
        assertSame(map["ground"], map.layerOrNull("ground"))
    }

    @Test
    fun rejectsACompressedLayerNamingTheFix() {
        assertEquals(
            "Layer 'ground' of levels/zlib.tmj is compressed with zlib; export the map with Tile Layer Format CSV, " +
                "or Base64 (uncompressed), in Tiled's Map Properties.",
            message("levels/zlib.tmj"),
        )
    }

    @Test
    fun rejectsAnInfiniteMap() {
        assertEquals(
            "levels/infinite.tmj is an infinite map, which klyph does not read; " +
                "untick Infinite in Tiled's Map Properties and save it again.",
            message("levels/infinite.tmj"),
        )
    }

    @Test
    fun rejectsMapsThatAreNotOrthogonal() {
        assertTrue("is an isometric map" in message("levels/isometric.tmj"))
    }

    @Test
    fun rejectsSeveralTilesets() {
        assertTrue("uses 2 tilesets" in message("levels/two-tilesets.tmj"))
    }

    @Test
    fun rejectsAMissingTilesetImageNamingIt() {
        val text = message("levels/no-image.tmj")
        assertTrue(text.startsWith("The image levels/missing.png of the tileset of levels/no-image.tmj could not be loaded ("), text)
        assertTrue(text.endsWith("; put it where the tileset names it, relative to the tileset's file."), text)
    }

    @Test
    fun rejectsAnXmlTileset() {
        assertTrue("is the XML file levels/tiles.tsx" in message("levels/tsx.tmj"))
    }

    @Test
    fun rejectsInvalidJsonNamingTheLine() {
        assertEquals(
            "levels/broken.tmj is not valid JSON: expected ',' or '}' but found '\"' at line 2, column 3; " +
                "save the map again from Tiled as JSON (.tmj).",
            message("levels/broken.tmj"),
        )
    }

    @Test
    fun rejectsAMissingFieldNamingIt() {
        assertEquals(
            "levels/no-width.tmj has no 'width', which must be a whole number; save the map again from Tiled as JSON (.tmj).",
            message("levels/no-width.tmj"),
        )
    }

    @Test
    fun rejectsTileIdsOutsideTheTileset() {
        assertTrue("has the tile id 9 at column 1, row 0" in message("levels/bad-id.tmj"), message("levels/bad-id.tmj"))
    }

    @Test
    fun orNullReturnsNullForMapsItCannotRead() {
        val assets = assets()
        assertNull(assets.tileMapOrNull("levels/zlib.tmj"))
        assertNull(assets.tileMapOrNull("levels/missing.tmj"))
        assertNotNull(assets.tileMapOrNull("levels/csv.tmj"))
        assertFailsWith<FileException> { assets.tileMap("levels/missing.tmj") }
    }

    @Test
    fun readTextAndReadBytesResolveUnderTheRoot() {
        val assets = assets()
        assertTrue(assets.readText("levels/csv.tmj").trim().startsWith("{\"type\": \"map\""))
        assertEquals(assets.readText("levels/csv.tmj"), assets.readBytes("levels/csv.tmj").decodeToString())
        assertNull(assets.readTextOrNull("levels/missing.tmj"))
        assertNull(assets.readBytesOrNull("levels/missing.tmj"))
        assertFailsWith<FileException> { assets.readText("levels/missing.tmj") }
        assets.close()
        assertFailsWith<IllegalStateException> { assets.readText("levels/csv.tmj") }
        assertFailsWith<IllegalStateException> { assets.tileMap("levels/csv.tmj") }
    }

    private companion object {
        val ROOT: String = Files.basePath + "klyph-tiles-test/"

        /** The embedded tileset of most fixtures: tiles 0 and 1 solid, 2 one-way, image next to the map. */
        fun tileset(image: String = "../tiles.png", firstId: Int = 1): String = """
            {"firstgid": $firstId, "name": "tiles", "image": "$image", "imagewidth": 40, "imageheight": 10,
             "columns": 4, "tilecount": 4, "tilewidth": 8, "tileheight": 8, "margin": 1, "spacing": 2,
             "tiles": [
               {"id": 0, "properties": [{"name": "solid", "type": "bool", "value": true}]},
               {"id": 1, "properties": [{"name": "solid", "type": "bool", "value": true},
                                        {"name": "note", "type": "string", "value": "brick"}]},
               {"id": 2, "properties": [{"name": "oneWay", "type": "bool", "value": true}]},
               {"id": 3, "properties": [{"name": "solid", "type": "bool", "value": false}]}
             ]}
        """.trimIndent()

        /** A map of 3 by 2 cells of 8 by 8 with [layers] and [tilesets], and [extra] members. */
        fun map(layers: String, tilesets: String = "[${tileset()}]", extra: String = ""): String = """
            {"type": "map", "version": "1.10", "tiledversion": "1.11.0", "orientation": "orthogonal",
             "renderorder": "right-down", "infinite": false, "width": 3, "height": 2,
             "tilewidth": 8, "tileheight": 8, $extra
             "layers": $layers, "tilesets": $tilesets}
        """.trimIndent()

        fun csvLayer(name: String, data: String, extra: String = ""): String =
            """{"id": 1, "name": "$name", "type": "tilelayer", "width": 3, "height": 2, "x": 0, "y": 0, """ +
                """"opacity": 1, "visible": true, $extra "data": [$data]}"""

        /** The base64 of the little-endian 32-bit [ids]. */
        fun base64(vararg ids: Long): String {
            val bytes = ByteArray(ids.size * 4)
            for ((index, id) in ids.withIndex()) for (k in 0 until 4) bytes[index * 4 + k] = (id shr (8 * k)).toByte()
            return Base64.encode(bytes)
        }

        val FILES: Map<String, String> = mapOf(
            "levels/csv.tmj" to map(
                "[" + csvLayer("ground", "1, 0, 2, 0, 3, 4") + ", " +
                    csvLayer("decor", "0, 0, 0, 0, 0, 0", extra = "\"opacity\": 0.5, \"visible\": false,") + "]",
            ),
            "levels/base64.tmj" to map(
                """[{"id": 1, "name": "ground", "type": "tilelayer", "width": 3, "height": 2, "encoding": "base64", "compression": "",
                    "data": "${base64(1, 0, 3, 4, 2, 0)}"}]""",
            ),
            "levels/external.tmj" to map(
                "[" + csvLayer("ground", "6, 0, 0, 0, 0, 0") + "]",
                tilesets = """[{"firstgid": 5, "source": "../sets/tiles.tsj"}]""",
            ),
            "sets/tiles.tsj" to """
                {"type": "tileset", "name": "tiles", "image": "../art/tiles.png", "imagewidth": 40, "imageheight": 10,
                 "columns": 4, "tilecount": 4, "tilewidth": 8, "tileheight": 8, "margin": 1, "spacing": 2,
                 "tiles": [{"id": 3, "properties": [{"name": "solid", "type": "bool", "value": true}]}]}
            """.trimIndent(),
            "levels/flips.tmj" to map("[" + csvLayer("ground", "2147483650, 1073741826, 2684354562, 268435458, 0, 0") + "]"),
            "levels/objects.tmj" to map(
                "[" + """{"id": 2, "name": "spawns", "type": "objectgroup", "objects": []}, """ +
                    """{"id": 3, "name": "sky", "type": "imagelayer", "image": "sky.png"}, """ +
                    csvLayer("ground", "1, 1, 1, 0, 0, 0") + ", " +
                    """{"id": 4, "name": "props", "type": "group", "layers": []}]""",
            ),
            "levels/offsets.tmj" to map(
                "[" + csvLayer("plain", "0, 0, 0, 0, 0, 0", extra = "\"offsetx\": 16.5, \"offsety\": -8,") + ", " +
                    csvLayer("ground", "1, 0, 0, 0, 0, 0") + "]",
            ),
            "levels/groups.tmj" to map(
                "[" + csvLayer("outer", "0, 0, 0, 0, 0, 0", extra = "\"offsetx\": 10, \"offsety\": 20,") + ", " +
                    """{"id": 5, "name": "world", "type": "group", "offsetx": 10, "offsety": 20, "layers": [""" +
                    """{"id": 6, "name": "spawns", "type": "objectgroup", "objects": []}, """ +
                    csvLayer("inner", "1, 0, 0, 0, 0, 0", extra = "\"offsetx\": 6,") + "]}]",
            ),
            "levels/group-looks.tmj" to map(
                "[" + """{"id": 5, "name": "off", "type": "group", "visible": false, "opacity": 0.5, "layers": [""" +
                    csvLayer("hidden", "0, 0, 0, 0, 0, 0") + "]}, " +
                    """{"id": 6, "name": "half", "type": "group", "opacity": 0.5, "layers": [""" +
                    csvLayer("faded", "0, 0, 0, 0, 0, 0", extra = "\"opacity\": 0.5,") + "]}, " +
                    csvLayer("plain", "0, 0, 0, 0, 0, 0") + "]",
            ),
            "levels/firstgid.tmj" to map("[]", tilesets = "[${tileset(firstId = 0)}]"),
            "levels/parallax.tmj" to map("[" + csvLayer("ground", "0, 0, 0, 0, 0, 0", extra = "\"parallaxx\": 0.5,") + "]"),
            "levels/tint.tmj" to map("[" + csvLayer("ground", "0, 0, 0, 0, 0, 0", extra = "\"tintcolor\": \"#ff0000\",") + "]"),
            "levels/tileoffset.tmj" to map(
                "[]",
                tilesets = "[${tileset().replace("\"margin\"", "\"tileoffset\": {\"x\": 4, \"y\": 0}, \"margin\"")}]",
            ),
            "levels/zlib.tmj" to map(
                """[{"id": 1, "name": "ground", "type": "tilelayer", "width": 3, "height": 2, "encoding": "base64",
                    "compression": "zlib", "data": "eJxjZGBgYAAAAAkAAQ=="}]""",
            ),
            "levels/infinite.tmj" to map("[]").replace("\"infinite\": false", "\"infinite\": true"),
            "levels/isometric.tmj" to map("[]").replace("\"orthogonal\"", "\"isometric\""),
            "levels/two-tilesets.tmj" to map("[]", tilesets = "[${tileset()}, ${tileset(firstId = 5)}]"),
            "levels/no-image.tmj" to map("[]", tilesets = "[${tileset(image = "missing.png")}]"),
            "levels/tsx.tmj" to map("[]", tilesets = """[{"firstgid": 1, "source": "tiles.tsx"}]"""),
            "levels/broken.tmj" to "{\"type\": \"map\"\n  \"width\": 3}",
            "levels/no-width.tmj" to map("[]").replace("\"width\": 3, ", ""),
            "levels/bad-id.tmj" to map("[" + csvLayer("ground", "1, 9, 0, 0, 0, 0") + "]"),
        )
    }
}
