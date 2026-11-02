package klyph.text

import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.platform.Color
import klyph.render.DrawSpace
import klyph.render.QuadBuffer
import klyph.render.SPRITE_RECORD_FLOATS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TextExtractionTest {
    /** Returns floats [from] until [until] of quad [index]'s record. */
    private fun QuadBuffer.floats(index: Int, from: Int, until: Int): List<Float> =
        (from until until).map { records[index * SPRITE_RECORD_FLOATS + it] }

    private fun assertNear(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertTrue(abs(expected[i] - actual[i]) < 1e-4f, "Expected $expected, got $actual.")
    }

    @Test
    fun twoLineTextGivesOneQuadPerGlyphBelowEachOther() {
        val font = testFont()
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform(x = 100f, y = 50f))
        val red = Color(red = 1f, green = 0f, blue = 0f)
        world.add(entity, Text, Text(font, "A\nA", color = red, scale = 2f, layer = 3))
        val quads = QuadBuffer()

        assertEquals(2, world.extractTexts(1f, quads))
        assertEquals(2, quads.count)
        // Glyph A: offset (1, 2), 8 x 10 texels, at scale 2: left 2, top 4, 16 x 20, centre (+10, +14).
        assertEquals(listOf(110f, 64f, 0f), quads.floats(0, 0, 3))
        assertEquals(listOf(16f, 20f), quads.floats(0, 4, 6))
        // The second line starts one line height of 16, times 2, lower.
        assertEquals(listOf(110f, 96f, 0f), quads.floats(1, 0, 3))
        assertEquals(listOf(1f, 0f, 0f, 1f), quads.floats(1, 12, 16))
        assertEquals(3, quads.layers[0])
        assertSame(DrawSpace.WORLD, quads.spaceAt(0))
        assertSame(font.glyphOrNull('A')?.region?.texture, quads.textureAt(0))
    }

    @Test
    fun entityScaleAndRotationApplyAboutTheAnchor() {
        val font = testFont()
        val world = World()
        val scaled = world.create()
        world.add(scaled, Transform, Transform(x = 100f, y = 50f, scaleX = 2f))
        world.add(scaled, Text, Text(font, "A"))
        val turned = world.create()
        world.add(turned, Transform, Transform(x = 100f, y = 50f, rotation = (PI / 2).toFloat()))
        world.add(turned, Text, Text(font, "A"))
        val quads = QuadBuffer()

        assertEquals(2, world.extractTexts(1f, quads))
        // Centre (5, 7) from the anchor; x doubled.
        assertEquals(listOf(110f, 57f), quads.floats(0, 0, 2))
        assertEquals(listOf(16f, 10f), quads.floats(0, 4, 6))
        // Centre (5, 7) turned a quarter clockwise is (-7, 5).
        assertNear(listOf(93f, 55f, (PI / 2).toFloat()), quads.floats(1, 0, 3))
    }

    @Test
    fun worldTransformWinsAndEntitiesWithoutTransformAreSkipped() {
        val font = testFont()
        val world = World()
        val child = world.create()
        world.add(child, Transform, Transform(x = 1f, y = 1f))
        world.add(child, WorldTransform, WorldTransform(x = 200f, y = 300f))
        world.add(child, Text, Text(font, "A"))
        val loose = world.create()
        world.add(loose, Text, Text(font, "AAAA"))
        val quads = QuadBuffer()

        assertEquals(1, world.extractTexts(1f, quads))
        assertEquals(listOf(205f, 307f), quads.floats(0, 0, 2))
    }

    @Test
    fun spacesAndEmptyTextDrawNothing() {
        val font = testFont()
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform())
        world.add(entity, Text, Text(font, "  \n "))
        assertEquals(0, world.extractTexts(1f, QuadBuffer()))
    }
}
