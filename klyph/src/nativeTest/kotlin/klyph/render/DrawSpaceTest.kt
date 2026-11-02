package klyph.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawSpaceTest {
    /** Applies the column-major [matrix] to the point ([x], [y]) and returns clip x and y. */
    private fun clip(matrix: FloatArray, x: Float, y: Float): Pair<Float, Float> =
        (matrix[0] * x + matrix[4] * y + matrix[12]) to (matrix[1] * x + matrix[5] * y + matrix[13])

    @Test
    fun screenProjectionMapsPointsFromTheTopLeftCorner() {
        val matrix = FloatArray(20) { 9f }
        writeScreenProjection(matrix, 800f, 600f)
        assertEquals(-1f to 1f, clip(matrix, 0f, 0f))
        assertEquals(1f to -1f, clip(matrix, 800f, 600f))
        assertEquals(0f to 0f, clip(matrix, 400f, 300f))
        assertEquals(1f, matrix[15])
        // Floats past the matrix are left alone, so the first-sprite uniform survives.
        assertEquals(9f, matrix[16])
    }

    @Test
    fun screenGroupsSortAfterWorldGroupsWhateverTheirLayer() {
        val a = Texture(8, 8)
        val order = SpriteOrder()
        order.begin()
        order.addGroup(order.groupFor(-100, a, DrawSpace.SCREEN))
        order.add(100, a)
        order.addGroup(order.groupFor(Int.MIN_VALUE, a, DrawSpace.SCREEN))
        order.add(Int.MAX_VALUE, a)
        order.finish()
        assertEquals(listOf(3, 0, 2, 1), (0 until 4).map { order.claimPosition(it) })
        // One texture: the world groups merge into one run and the screen groups into another.
        assertEquals(1, order.firstScreenRun)
        assertEquals(listOf(0 to 2, 2 to 2), (0 until order.runCount).map { order.runFirst(it) to order.runSize(it) })
    }

    @Test
    fun firstScreenRunIsTheRunCountWithoutScreenGroups() {
        val a = Texture(8, 8)
        val order = SpriteOrder()
        order.begin()
        order.add(0, a)
        order.finish()
        assertEquals(1, order.firstScreenRun)

        order.begin()
        order.addGroup(order.groupFor(0, a, DrawSpace.SCREEN))
        order.finish()
        assertEquals(0, order.firstScreenRun)
        assertEquals(1, order.runCount)
    }

    @Test
    fun screenAndWorldGroupsOfOneLayerAndTextureStayApart() {
        val a = Texture(8, 8)
        val order = SpriteOrder()
        order.begin()
        order.addGroupWithoutPosition(order.groupFor(0, a, DrawSpace.SCREEN))
        order.addWithoutPosition(0, a)
        order.addGroupWithoutPosition(order.groupFor(0, a, DrawSpace.WORLD))
        order.finish()
        assertFalse(order.isSorted)
        assertEquals(2, order.runCount)
        assertEquals(listOf(2, 1), (0 until 2).map { order.runSize(it) })

        // The same groups again: place predicts them, also for the screen group.
        order.begin()
        val placed = listOf(
            order.placeGroup(order.groupFor(0, a, DrawSpace.SCREEN)),
            order.place(0, a),
            order.placeGroup(order.groupFor(0, a, DrawSpace.WORLD)),
        )
        order.finish()
        assertTrue(order.predictionHeld)
        assertEquals(listOf(2, 0, 1), placed)
    }
}
