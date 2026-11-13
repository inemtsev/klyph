package klyph.render

import klyph.entity.Entity
import klyph.entity.Interpolated
import klyph.entity.PreviousTransform
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.entity.drawnPositionOf
import klyph.entity.propagateTransforms
import klyph.entity.recordPreviousTransforms
import klyph.particle.ParticleEmitter
import klyph.particle.advanceParticles
import klyph.particle.extractParticles
import klyph.platform.SamplerAddress
import klyph.text.Text
import klyph.text.extractTexts
import klyph.text.testFont
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SpriteRecordTest {
    private val region = Texture(8, 8).asRegion()

    /** Returns the record the sprite pass writes for [entity] at [alpha], as its lookups find the transforms. */
    private fun World.recordOf(entity: Entity, alpha: Float): List<Float> {
        val floats = FloatArray(SPRITE_RECORD_FLOATS)
        val hierarchy = getOrNull(entity, WorldTransform)
        val previous = if (hierarchy == null) getOrNull(entity, PreviousTransform) else null
        writeRecord(this, FloatArray(4), 0, get(entity, Transform), hierarchy, previous, alpha, get(entity, Sprite)) {
            index, value -> floats[index] = value
        }
        return floats.toList()
    }

    /** Returns the record of [sprite] as the first entity of a new world, at ([x], [y]) and turned by [rotation]. */
    private fun recordOfSprite(
        sprite: Sprite,
        x: Float = 100f,
        y: Float = 50f,
        rotation: Float = 0f,
        scaleX: Float = 1f,
    ): List<Float> {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform, Transform(x = x, y = y, rotation = rotation, scaleX = scaleX))
        world.add(entity, Sprite, sprite)
        return world.recordOf(entity, alpha = 1f)
    }

    private fun assertNear(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size, "Expected $expected, got $actual.")
        for (i in expected.indices) assertTrue(abs(expected[i] - actual[i]) < 1e-4f, "Expected $expected, got $actual.")
    }

    @Test
    fun anInterpolatedSpriteIsRecordedWhereDrawnPositionOfPutsIt() {
        val world = World()
        val entity = world.create()
        val transform = Transform(x = 100f, y = 40f)
        world.add(entity, Transform, transform)
        world.add(entity, Sprite, Sprite(region))
        world.add(entity, Interpolated, Interpolated)
        world.recordPreviousTransforms()
        transform.x = 130f
        world.recordPreviousTransforms()
        transform.x = 160f
        transform.y = 80f
        transform.scaleX = 3f

        for (alpha in listOf(0f, 0.25f, 0.5f, 1f)) {
            val drawn = world.drawnPositionOf(entity, alpha)
            assertEquals(listOf(drawn.x, drawn.y), world.recordOf(entity, alpha).subList(0, 2), "Alpha $alpha")
        }
        // Halfway from (130, 40) to (160, 80), and from a scale of 1 to 3 across: 8 texels become 16.
        val record = world.recordOf(entity, 0.5f)
        assertEquals(listOf(145f, 60f), record.subList(0, 2))
        assertEquals(listOf(16f, 8f), record.subList(4, 6))
    }

    @Test
    fun aSpriteInItsFirstStepOrBelowAParentIsRecordedWhereDrawnPositionOfPutsIt() {
        val world = World()
        val fresh = world.create()
        val freshTransform = Transform(x = 10f, y = 10f)
        world.add(fresh, Transform, freshTransform)
        world.add(fresh, Sprite, Sprite(region))
        world.add(fresh, Interpolated, Interpolated)
        val parent = world.create()
        world.add(parent, Transform, Transform(x = 200f, y = 80f))
        val child = world.create()
        world.add(child, Transform, Transform(x = 10f))
        world.add(child, Sprite, Sprite(region))
        world.add(child, Interpolated, Interpolated)
        world.setParent(child, parent)
        world.recordPreviousTransforms()
        freshTransform.x = 50f
        world.propagateTransforms()

        for (entity in listOf(fresh, child)) {
            val drawn = world.drawnPositionOf(entity, 0.5f)
            assertEquals(listOf(drawn.x, drawn.y), world.recordOf(entity, 0.5f).subList(0, 2), "Entity $entity")
        }
        assertEquals(listOf(50f, 10f), world.recordOf(fresh, 0.5f).subList(0, 2), "The first step is drawn where the sprite is.")
        assertEquals(listOf(210f, 80f), world.recordOf(child, 0.5f).subList(0, 2), "A child is drawn at its world transform.")
    }

    @Test
    fun aSpriteRecordsItsOriginAfterItsSize() {
        assertEquals(listOf(8f, 8f, 0.5f, 0.5f), recordOfSprite(Sprite(region)).subList(4, 8), "The center, by default.")
        val record = recordOfSprite(Sprite(region, width = 20f, height = 10f, originX = 0.25f, originY = 1f))
        assertEquals(listOf(100f, 50f), record.subList(0, 2), "The origin does not move the position of the record.")
        assertEquals(listOf(20f, 10f, 0.25f, 1f), record.subList(4, 8))
    }

    @Test
    fun textAndParticleRecordsHaveTheCenterAsTheirOrigin() {
        val world = World()
        val label = world.create()
        world.add(label, Transform, Transform(x = 10f, y = 20f))
        world.add(label, Text, Text(testFont(), "AV"))
        val emitter = ParticleEmitter(region).apply {
            speed = 0f..0f
            lifetime = 10.seconds..10.seconds
        }
        val fountain = world.create()
        world.add(fountain, Transform, Transform(x = 30f, y = 40f))
        world.add(fountain, ParticleEmitter, emitter)
        emitter.emit(2)
        world.advanceParticles(0f)

        val quads = QuadBuffer()
        assertEquals(2, world.extractTexts(1f, quads))
        assertEquals(2, world.extractParticles(quads))
        assertEquals(4, quads.count)
        for (quad in 0 until quads.count) {
            val origin = (6 until 8).map { quads.records[quad * SPRITE_RECORD_FLOATS + it] }
            assertEquals(listOf(0.5f, 0.5f), origin, "Quad $quad")
        }
    }

    @Test
    fun anOriginOutsideTheQuadIsRecordedAsItIs() {
        val record = recordOfSprite(Sprite(region, originX = -1f, originY = 2.5f))
        assertEquals(listOf(-1f, 2.5f), record.subList(6, 8))
        // The transform is one width left of the quad's left edge and 2.5 heights below its top: the quad lies right of and above it.
        assertNear(listOf(108f, 30f, 116f, 30f, 108f, 38f, 116f, 38f), ShaderQuad(record).corners)
    }

    @Test
    fun aQuarterTurnAboutAnOriginOnTheBottomEdgeKeepsTheBottomCenterWhereTheTransformIs() {
        // 20 by 10 with the origin in the middle of the bottom edge, where a character's feet go.
        val sprite = Sprite(region, width = 20f, height = 10f, originX = 0.5f, originY = 1f)
        val upright = ShaderQuad(recordOfSprite(sprite))
        assertNear(listOf(90f, 40f, 110f, 40f, 90f, 50f, 110f, 50f), upright.corners)
        assertNear(listOf(100f, 50f), upright.positionOf(0.5f, 1f))

        val turned = ShaderQuad(recordOfSprite(sprite, rotation = (PI / 2).toFloat()))
        // Clockwise on screen, the bottom edge now stands on the transform and the top edge is 10 to its right.
        assertNear(listOf(110f, 40f, 110f, 60f, 100f, 40f, 100f, 60f), turned.corners)
        assertNear(listOf(100f, 50f), turned.positionOf(0.5f, 1f))
        assertNear(listOf(110f, 50f), turned.positionOf(0.5f, 0f))
    }

    @Test
    fun theScaleGrowsTheQuadAwayFromTheOrigin() {
        val sprite = Sprite(region, originX = 0.25f)
        assertNear(listOf(98f, 46f, 106f, 46f, 98f, 54f, 106f, 54f), ShaderQuad(recordOfSprite(sprite)).corners)
        val doubled = recordOfSprite(sprite, scaleX = 2f)
        assertEquals(listOf(16f, 8f, 0.25f, 0.5f), doubled.subList(4, 8))
        assertNear(listOf(96f, 46f, 112f, 46f, 96f, 54f, 112f, 54f), ShaderQuad(doubled).corners)
        assertNear(listOf(100f, 50f), ShaderQuad(doubled).positionOf(0.25f, 0.5f))
    }

    @Test
    fun aFlipMirrorsThePictureAboutTheOriginAndLeavesThePointOnItWhereItIs() {
        val plain = ShaderQuad(recordOfSprite(Sprite(region, originX = 0.25f)))
        val flipped = ShaderQuad(recordOfSprite(Sprite(region, isFlippedX = true, originX = 0.25f)))
        // The texel a quarter of the way across the region is the origin's own, and is on the transform either way.
        assertNear(listOf(100f, 50f), plain.positionOfTexel(0.25f, 0.5f))
        assertNear(listOf(100f, 50f), flipped.positionOfTexel(0.25f, 0.5f))
        // The left edge of the region is 2 units left of the transform, or 2 units right once flipped; the right edge 6 right or 6 left.
        assertNear(listOf(98f, 50f), plain.positionOfTexel(0f, 0.5f))
        assertNear(listOf(102f, 50f), flipped.positionOfTexel(0f, 0.5f))
        assertNear(listOf(106f, 50f), plain.positionOfTexel(1f, 0.5f))
        assertNear(listOf(94f, 50f), flipped.positionOfTexel(1f, 0.5f))

        val plainDown = ShaderQuad(recordOfSprite(Sprite(region, originY = 0.25f)))
        val flippedDown = ShaderQuad(recordOfSprite(Sprite(region, isFlippedY = true, originY = 0.25f)))
        assertNear(listOf(100f, 50f), plainDown.positionOfTexel(0.5f, 0.25f))
        assertNear(listOf(100f, 50f), flippedDown.positionOfTexel(0.5f, 0.25f))
        assertNear(listOf(100f, 48f), plainDown.positionOfTexel(0.5f, 0f))
        assertNear(listOf(100f, 52f), flippedDown.positionOfTexel(0.5f, 0f))
        assertNear(listOf(100f, 56f), plainDown.positionOfTexel(0.5f, 1f))
        assertNear(listOf(100f, 44f), flippedDown.positionOfTexel(0.5f, 1f))

        val both = ShaderQuad(recordOfSprite(Sprite(region, isFlippedX = true, isFlippedY = true, originX = 0.25f, originY = 0.25f)))
        assertNear(listOf(100f, 50f), both.positionOfTexel(0.25f, 0.25f))
        assertNear(listOf(94f, 44f), both.positionOfTexel(1f, 1f))
    }

    @Test
    fun aFlipAboutTheCenterKeepsTheQuadWhereItIsAndOnlyTurnsTheTexels() {
        val plain = ShaderQuad(recordOfSprite(Sprite(region)))
        val flipped = ShaderQuad(recordOfSprite(Sprite(region, isFlippedX = true)))
        assertEquals(plain.corners, flipped.corners)
        assertEquals(listOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f), plain.cornerTexels)
        assertEquals(listOf(1f, 0f, 0f, 0f, 1f, 1f, 0f, 1f), flipped.cornerTexels)
    }

    @Test
    fun aTiledSpriteWithAnOriginStillTilesFromTheTopLeftCornerOfItsQuad() {
        val ground = Texture(16, 16, address = SamplerAddress.REPEAT).asRegion()
        val sprite = Sprite(ground, width = 80f, height = 48f, tileWidth = 16f, tileHeight = 16f, originX = 0f, originY = 1f)
        val quad = ShaderQuad(recordOfSprite(sprite))
        // The origin is the bottom-left corner, on the transform, so the quad lies right of and above (100, 50).
        assertNear(listOf(100f, 2f, 180f, 2f, 100f, 50f, 180f, 50f), quad.corners)
        // The repeats count from the top-left corner whatever the origin: 5 across and 3 down.
        assertEquals(listOf(0f, 0f, 5f, 0f, 0f, 3f, 5f, 3f), quad.cornerTexels)

        // Flipped, the whole tiled quad mirrors about the origin: it lies left of the transform, counting from its right edge.
        val mirrored = Sprite(
            ground,
            width = 80f,
            height = 48f,
            isFlippedX = true,
            tileWidth = 16f,
            tileHeight = 16f,
            originX = 0f,
            originY = 1f,
        )
        val flipped = ShaderQuad(recordOfSprite(mirrored))
        assertNear(listOf(20f, 2f, 100f, 2f, 20f, 50f, 100f, 50f), flipped.corners)
        assertEquals(listOf(5f, 0f, 0f, 0f, 5f, 3f, 0f, 3f), flipped.cornerTexels)
    }

    @Test
    fun anOriginThatIsInfiniteOrNotANumberFailsNamingTheEntityOfTheSprite() {
        val world = World()
        val fine = world.create()
        world.add(fine, Transform, Transform())
        world.add(fine, Sprite, Sprite(region, originX = 7f, originY = -7f))
        val broken = world.create()
        world.add(broken, Transform, Transform())
        world.add(broken, Sprite, Sprite(region, originX = Float.NaN))
        world.recordOf(fine, 1f)

        val error = assertFailsWith<IllegalStateException> { world.recordOf(broken, 1f) }
        assertEquals("Entity(1:0) has a Sprite with origin NaNx0.5, which is infinite or not a number.", error.message)

        val cases = listOf(
            Sprite(region, originX = Float.POSITIVE_INFINITY) to "Infinityx0.5",
            Sprite(region, originX = Float.NEGATIVE_INFINITY) to "-Infinityx0.5",
            Sprite(region, originY = Float.NaN) to "0.5xNaN",
            Sprite(region, originY = Float.POSITIVE_INFINITY) to "0.5xInfinity",
            Sprite(region, originY = Float.NEGATIVE_INFINITY) to "0.5x-Infinity",
        )
        for ((sprite, origin) in cases) {
            val single = World()
            val entity = single.create()
            single.add(entity, Transform, Transform())
            single.add(entity, Sprite, sprite)
            val failure = assertFailsWith<IllegalStateException> { single.recordOf(entity, 1f) }
            assertEquals("Entity(0:0) has a Sprite with origin $origin, which is infinite or not a number.", failure.message)
        }
    }
}
