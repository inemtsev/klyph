package klyph.text

import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.entity.drawnPositionOf
import klyph.entity.propagateTransforms
import klyph.entity.recordPreviousTransforms
import klyph.platform.Color
import klyph.platform.withAlpha
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
    fun interpolatedTextIsDrawnAtItsCurrentTransformAfterItsFirstStep() {
        val font = testFont()
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 0f, y = 0f)
        world.add(entity, Transform, transform)
        world.add(entity, Interpolated, Interpolated)
        world.add(entity, Text, Text(font, "A"))

        world.recordPreviousTransforms()
        transform.x = 100f
        val first = QuadBuffer()
        world.extractTexts(0.5f, first)
        // Glyph A at scale 1: left 1, top 2, 8 x 10 texels, centre (+5, +7).
        assertEquals(listOf(105f, 7f), first.floats(0, 0, 2), "No sweep from the spawn point at x = 0.")

        world.recordPreviousTransforms()
        transform.x = 110f
        val second = QuadBuffer()
        world.extractTexts(0.5f, second)
        assertEquals(listOf(110f, 7f), second.floats(0, 0, 2), "From the second step on, it blends halfway from 100 to 110.")
    }

    @Test
    fun textIsDrawnWhereDrawnPositionOfSaysForEveryKindOfEntity() {
        val font = testFont()
        val world = World()
        val alpha = 0.25f

        // A root past its first step: it blends from 130 to 160, so a quarter of the way is 137.5.
        val blended = world.create()
        val blendedTransform = Transform(x = 100f, y = 40f)
        world.add(blended, Transform, blendedTransform)
        world.add(blended, Interpolated, Interpolated)
        world.add(blended, Text, Text(font, "A"))
        world.recordPreviousTransforms()

        // A root in its first step, which is drawn where it is, at 50.
        val fresh = world.create()
        val freshTransform = Transform(x = 10f, y = 10f)
        world.add(fresh, Transform, freshTransform)
        world.add(fresh, Interpolated, Interpolated)
        world.add(fresh, Text, Text(font, "A"))
        blendedTransform.x = 130f
        world.recordPreviousTransforms()
        blendedTransform.x = 160f
        freshTransform.x = 50f

        val plain = world.create()
        world.add(plain, Transform, Transform(x = 70f, y = 20f))
        world.add(plain, Text, Text(font, "A"))

        // A child is drawn at its world transform, which a propagation computes from the parent's Transform.
        val parent = world.create()
        world.add(parent, Transform, Transform(x = 200f, y = 80f))
        val child = world.create()
        world.add(child, Transform, Transform(x = 10f))
        world.add(child, Text, Text(font, "A"))
        world.setParent(child, parent)
        world.propagateTransforms()

        val quads = QuadBuffer()
        val entities = listOf(blended, fresh, plain, child)
        assertEquals(entities.size, world.extractTexts(alpha, quads))

        // Glyph A at scale 1 is centred (+5, +7) from the anchor, as above.
        for ((index, entity) in entities.withIndex()) {
            val drawn = world.drawnPositionOf(entity, alpha)
            assertEquals(listOf(drawn.x + 5f, drawn.y + 7f), quads.floats(index, 0, 2), "Quad $index, entity $entity")
        }
        // The anchors are 137.5, 50, 70 and 210, so the loop above is checking real places and not the function against itself.
        assertEquals(listOf(142.5f, 55f, 75f, 215f), entities.indices.map { quads.floats(it, 0, 1).single() })
    }

    @Test
    fun shadowGlyphsComeFirstInTheSameLayerOffsetAlongTheBlock() {
        val font = testFont()
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform(x = 100f, y = 50f, scaleX = 2f, scaleY = 2f))
        val text = Text(font, StringBuilder("A"), color = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.5f), layer = 3)
        text.shadow = TextShadow(offsetX = 1f, offsetY = 3f, color = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.8f))
        world.add(entity, Text, text)
        val quads = QuadBuffer()

        assertEquals(2, world.extractTexts(1f, quads))
        // The glyph centre is (+5, +7) times the entity's scale of 2; the shadow is 1 by 3 further, also times 2.
        assertEquals(listOf(112f, 70f), quads.floats(0, 0, 2))
        assertEquals(listOf(110f, 64f), quads.floats(1, 0, 2))
        // The shadow's alpha is its own times the text's, rounded to a byte.
        val shadowAlpha = text.shadow!!.color.withAlpha(text.shadow!!.color.alpha * text.color.alpha).alpha
        assertTrue(shadowAlpha in 0.39f..0.41f)
        assertNear(listOf(0f, 0f, 0f, shadowAlpha), quads.floats(0, 12, 16))
        assertNear(listOf(1f, 1f, 1f, text.color.alpha), quads.floats(1, 12, 16))
        assertEquals(3, quads.layers[0])
        assertEquals(3, quads.layers[1])
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
