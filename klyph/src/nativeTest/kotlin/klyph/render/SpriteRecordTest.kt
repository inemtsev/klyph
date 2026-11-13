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
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
