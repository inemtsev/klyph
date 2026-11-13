package klyph.render

import klyph.platform.Color
import klyph.platform.SamplerAddress
import klyph.text.BuiltInFont
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CanvasTest {
    private val builtIn = BuiltInFont.over(Texture(BuiltInFont.ATLAS_WIDTH, BuiltInFont.ATLAS_HEIGHT))

    private fun newCanvas(): Canvas = Canvas(builtIn.whitePixel)

    /** Returns floats [from] until [until] of quad [index]'s record. */
    private fun Canvas.floats(index: Int, from: Int, until: Int): List<Float> =
        (from until until).map { quads.records[index * SPRITE_RECORD_FLOATS + it] }

    private fun assertNear(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertTrue(abs(expected[i] - actual[i]) < 1e-4f, "Expected $expected, got $actual.")
    }

    @Test
    fun callsOutsideRenderThrow() {
        val canvas = newCanvas()
        val region = Texture(4, 4).asRegion()
        assertFailsWith<IllegalStateException> { canvas.drawSprite(region, x = 0f, y = 0f) }
        assertFailsWith<IllegalStateException> { canvas.drawTiled(region, x = 0f, y = 0f, width = 8f, height = 8f) }
        assertFailsWith<IllegalStateException> { canvas.drawRect(x = 0f, y = 0f, width = 1f, height = 1f, color = Color.WHITE) }
        assertFailsWith<IllegalStateException> { canvas.drawText(builtIn.font, "hi", x = 0f, y = 0f) }
        assertEquals(0, canvas.quadCount)

        canvas.open()
        canvas.drawSprite(region, x = 0f, y = 0f)
        assertEquals(1, canvas.quadCount)
        canvas.finish()
        assertFailsWith<IllegalStateException> { canvas.drawSprite(region, x = 0f, y = 0f) }
    }

    @Test
    fun finishEmptiesTheList() {
        val canvas = newCanvas()
        canvas.open()
        canvas.drawRect(x = 0f, y = 0f, width = 1f, height = 1f, color = Color.WHITE)
        canvas.drawText(builtIn.font, "ab c", x = 0f, y = 0f)
        // The space draws nothing.
        assertEquals(4, canvas.quadCount)
        assertEquals("Canvas(quadCount=4)", canvas.toString())
        canvas.finish()
        assertEquals(0, canvas.quadCount)
        canvas.open()
        assertEquals(0, canvas.quadCount)
    }

    @Test
    fun rectFillsFromItsTopLeftCornerWithTheWhitePixel() {
        val canvas = newCanvas()
        canvas.open()
        val color = Color(red = 0f, green = 1f, blue = 0f, alpha = 0.5f)
        canvas.drawRect(x = 10f, y = 20f, width = 30f, height = 40f, color = color, layer = 2, space = DrawSpace.SCREEN)
        assertEquals(listOf(25f, 40f, 0f), canvas.floats(0, 0, 3))
        assertEquals(listOf(30f, 40f), canvas.floats(0, 4, 6))
        val white = builtIn.whitePixel
        assertEquals(listOf(white.u0, white.v0, white.u1, white.v1), canvas.floats(0, 8, 12))
        assertEquals(listOf(0f, 1f, 0f, color.alpha), canvas.floats(0, 12, 16))
        assertEquals(2, canvas.quads.layers[0])
        assertSame(DrawSpace.SCREEN, canvas.quads.spaceAt(0))
    }

    @Test
    fun spriteOriginPlacesAndTurnsTheQuad() {
        val canvas = newCanvas()
        canvas.open()
        val region = Texture(32, 32).region(0, 0, 20, 10)
        canvas.drawSprite(region, x = 5f, y = 5f, space = DrawSpace.WORLD)
        canvas.drawSprite(region, x = 10f, y = 10f, originX = 0f, originY = 0f)
        canvas.drawSprite(region, x = 10f, y = 10f, rotation = (PI / 2).toFloat(), originX = 0f, originY = 0f)
        // Centre origin: the quad is centred on the point, at the region's size.
        assertEquals(listOf(5f, 5f, 0f, 0f, 20f, 10f), canvas.floats(0, 0, 6))
        // Top-left origin: the centre is half the size further on.
        assertEquals(listOf(20f, 15f), canvas.floats(1, 0, 2))
        // A quarter turn clockwise about the top-left corner takes the centre offset (10, 5) to (-5, 10).
        assertNear(listOf(5f, 20f, (PI / 2).toFloat()), canvas.floats(2, 0, 3))
        assertSame(DrawSpace.WORLD, canvas.quads.spaceAt(0))
    }

    @Test
    fun everyDrawCallDefaultsToScreenSpace() {
        val canvas = newCanvas()
        canvas.open()
        val region = Texture(4, 4).asRegion()
        canvas.drawSprite(region, x = 0f, y = 0f)
        canvas.drawTiled(Texture(4, 4, address = SamplerAddress.REPEAT).asRegion(), x = 0f, y = 0f, width = 8f, height = 8f)
        canvas.drawRect(x = 0f, y = 0f, width = 1f, height = 1f, color = Color.WHITE)
        canvas.drawText(builtIn.font, "A", x = 0f, y = 0f)
        for (quad in 0 until 4) assertSame(DrawSpace.SCREEN, canvas.quads.spaceAt(quad))
    }

    @Test
    fun tiledSpriteRepeatsTheTextureAtItsOwnSizeByDefault() {
        val canvas = newCanvas()
        canvas.open()
        val region = Texture(16, 16, address = SamplerAddress.REPEAT).asRegion()
        canvas.drawTiled(region, x = 100f, y = 50f, width = 80f, height = 40f, space = DrawSpace.WORLD)
        // Centred on (100, 50) like drawSprite, 80 by 40 units of 16 by 16 tiles: 5 across, 2.5 down.
        assertEquals(listOf(100f, 50f, 0f, 0f, 80f, 40f), canvas.floats(0, 0, 6))
        assertEquals(listOf(0f, 0f, 5f, 2.5f), canvas.floats(0, 8, 12))
        assertEquals(listOf(1f, 1f, 1f, 1f), canvas.floats(0, 12, 16))
        assertEquals(1, canvas.quadCount)
        assertSame(DrawSpace.WORLD, canvas.quads.spaceAt(0))
        assertSame(region.texture, canvas.quads.textureAt(0))
    }

    @Test
    fun tiledSpriteTakesTheTileSizeInItsOwnUnits() {
        val canvas = newCanvas()
        canvas.open()
        val region = Texture(16, 16, address = SamplerAddress.REPEAT).asRegion()
        canvas.drawTiled(region, x = 0f, y = 0f, width = 64f, height = 64f, tileWidth = 32f, tileHeight = 8f)
        canvas.drawTiled(region, x = 0f, y = 0f, width = 10f, height = 10f, tileWidth = 20f, tileHeight = 20f)
        assertEquals(listOf(0f, 0f, 2f, 8f), canvas.floats(0, 8, 12))
        assertEquals(listOf(0f, 0f, 0.5f, 0.5f), canvas.floats(1, 8, 12), "A tile larger than the quad shows part of one tile.")
    }

    @Test
    fun tiledSpriteKeepsTheOriginRotationColorAndLayerOfADrawSprite() {
        val canvas = newCanvas()
        canvas.open()
        val region = Texture(16, 16, address = SamplerAddress.REPEAT).asRegion()
        val color = Color(red = 0.5f, green = 0.25f, blue = 1f, alpha = 0.5f)
        canvas.drawTiled(region, x = 10f, y = 10f, width = 20f, height = 10f, originX = 0f, originY = 0f, color = color, layer = 7)
        canvas.drawTiled(region, x = 10f, y = 10f, width = 20f, height = 10f, rotation = (PI / 2).toFloat(), originX = 0f, originY = 0f)
        // Top-left origin: the centre is half the size further on, as for drawSprite.
        assertEquals(listOf(20f, 15f, 0f, 0f, 20f, 10f), canvas.floats(0, 0, 6))
        assertEquals(listOf(color.red, color.green, color.blue, color.alpha), canvas.floats(0, 12, 16))
        assertEquals(7, canvas.quads.layers[0])
        // A quarter turn clockwise about the top-left corner takes the centre offset (10, 5) to (-5, 10).
        assertNear(listOf(5f, 20f, (PI / 2).toFloat()), canvas.floats(1, 0, 3))
        assertEquals(listOf(0f, 0f, 1.25f, 0.625f), canvas.floats(1, 8, 12))
    }

    @Test
    fun tiledQuadsKeepTheirCoordinatesWhenTheBufferGrows() {
        val canvas = newCanvas()
        canvas.open()
        val region = Texture(16, 16, address = SamplerAddress.REPEAT).asRegion()
        // The quad buffer starts with room for 64 quads and replaces its arrays when it grows past them.
        for (i in 0 until 100) canvas.drawTiled(region, x = i.toFloat(), y = 0f, width = 16f * (i + 1), height = 16f)
        assertEquals(100, canvas.quadCount)
        assertEquals(listOf(0f, 0f, 1f, 1f), canvas.floats(0, 8, 12))
        assertEquals(listOf(0f, 0f, 65f, 1f), canvas.floats(64, 8, 12))
        assertEquals(listOf(0f, 0f, 100f, 1f), canvas.floats(99, 8, 12))
    }

    @Test
    fun tiledSpriteNeedsARepeatingTextureAWholeRegionAndPositiveTiles() {
        val canvas = newCanvas()
        canvas.open()
        val clamped = Texture(16, 16).asRegion()
        val repeating = Texture(16, 16, address = SamplerAddress.REPEAT)
        val address = assertFailsWith<IllegalArgumentException> { canvas.drawTiled(clamped, x = 0f, y = 0f, width = 32f, height = 32f) }
        assertEquals(
            "Tiled drawing needs a texture created with SamplerAddress.REPEAT, but Texture(16x16) uses CLAMP.",
            address.message,
        )
        val part = repeating.region(x = 0, y = 0, width = 8, height = 16)
        val region = assertFailsWith<IllegalArgumentException> { canvas.drawTiled(part, x = 0f, y = 0f, width = 32f, height = 32f) }
        assertEquals(
            "Tiled drawing needs a region that covers its whole texture, but the region is " +
                "TextureRegion(x=0, y=0, size=8x16, texture=Texture(16x16, REPEAT)).",
            region.message,
        )
        val whole = repeating.asRegion()
        val tileWidth = assertFailsWith<IllegalArgumentException> {
            canvas.drawTiled(whole, x = 0f, y = 0f, width = 32f, height = 32f, tileWidth = 0f)
        }
        assertEquals("Tile width 0.0 is not positive and finite.", tileWidth.message)
        val tileHeight = assertFailsWith<IllegalArgumentException> {
            canvas.drawTiled(whole, x = 0f, y = 0f, width = 32f, height = 32f, tileHeight = -4f)
        }
        assertEquals("Tile height -4.0 is not positive and finite.", tileHeight.message)
        assertFailsWith<IllegalArgumentException> {
            canvas.drawTiled(whole, x = 0f, y = 0f, width = 32f, height = 32f, tileWidth = Float.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> {
            canvas.drawTiled(whole, x = 0f, y = 0f, width = 32f, height = 32f, tileWidth = Float.NaN)
        }
        assertEquals(0, canvas.quadCount, "A rejected call queues nothing.")
    }

    @Test
    fun textAlignsOnTheAnchor() {
        val canvas = newCanvas()
        canvas.open()
        canvas.drawText(builtIn.font, "AB", x = 100f, y = 10f, scale = 2f, align = klyph.text.TextAlign.RIGHT)
        assertEquals(2, canvas.quadCount)
        assertSame(DrawSpace.SCREEN, canvas.quads.spaceAt(0))
        // The line is 32 wide at scale 2 and ends at x = 100: A covers 68..84, B 84..100.
        assertEquals(listOf(76f, 18f), canvas.floats(0, 0, 2))
        assertEquals(listOf(92f, 18f), canvas.floats(1, 0, 2))
        assertEquals(listOf(16f, 16f), canvas.floats(1, 4, 6))
    }

    @Test
    fun textShadowIsQueuedBeforeTheTextInTheSameLayer() {
        val canvas = newCanvas()
        canvas.open()
        val shadow = klyph.text.TextShadow(offsetX = 3f, offsetY = 4f, color = Color(0x000000FFu))
        canvas.drawText(builtIn.font, "AB", x = 100f, y = 10f, scale = 2f, layer = 5, shadow = shadow)
        assertEquals(4, canvas.quadCount, "Every glyph twice: the shadow pass, then the text.")
        // The text's A covers 100..116 and 10..26, centred on (108, 18); the shadow is 3 right and 4 down.
        assertEquals(listOf(111f, 22f), canvas.floats(0, 0, 2))
        assertEquals(listOf(127f, 22f), canvas.floats(1, 0, 2))
        assertEquals(listOf(108f, 18f), canvas.floats(2, 0, 2))
        assertEquals(listOf(0f, 0f, 0f, 1f), canvas.floats(0, 12, 16))
        assertEquals(listOf(1f, 1f, 1f, 1f), canvas.floats(2, 12, 16))
        for (quad in 0 until 4) assertEquals(5, canvas.quads.layers[quad])
    }

    @Test
    fun recordsSortScreenAfterWorldThenByLayerThenTexture() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val canvas = newCanvas()
        canvas.open()
        canvas.drawSprite(a.asRegion(), x = 0f, y = 0f, space = DrawSpace.SCREEN)
        canvas.drawSprite(b.asRegion(), x = 0f, y = 0f, space = DrawSpace.WORLD)
        canvas.drawSprite(b.asRegion(), x = 0f, y = 0f, layer = -1, space = DrawSpace.SCREEN)
        canvas.drawSprite(b.asRegion(), x = 0f, y = 0f, layer = 1, space = DrawSpace.WORLD)

        // Two world sprites first, as the renderer adds entity sprites before the canvas.
        val order = SpriteOrder()
        order.begin()
        order.add(0, a)
        order.add(1, b)
        val quads = canvas.quads
        for (quad in 0 until quads.count) {
            order.addGroup(order.groupFor(quads.layers[quad], quads.textureAt(quad), quads.spaceAt(quad)))
        }
        order.finish()
        val positions = (0 until 6).map { order.claimPosition(it) }

        // Sorted: (WORLD, 0, a) = 0; (WORLD, 0, b) = 3; (WORLD, 1, b) = 1, 5; (SCREEN, -1, b) = 4; (SCREEN, 0, a) = 2.
        assertEquals(listOf(0, 2, 5, 1, 4, 3), positions)
        val runs = (0 until order.runCount).map { Triple(order.runTexture(it), order.runFirst(it), order.runSize(it)) }
        // The world runs of b merge across layers; the screen run of b does not merge into them.
        assertEquals(listOf(Triple(a, 0, 1), Triple(b, 1, 3), Triple(b, 4, 1), Triple(a, 5, 1)), runs)
        assertEquals(2, order.firstScreenRun)
        assertTrue(!order.isSorted)
    }
}
