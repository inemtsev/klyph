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
        assertEquals(0, order.firstWorldRun)
        assertEquals(1, order.firstScreenRun)
        assertEquals(listOf(0 to 2, 2 to 2), (0 until order.runCount).map { order.runFirst(it) to order.runSize(it) })
    }

    @Test
    fun backgroundGroupsSortBeforeWorldGroupsWhateverTheirLayer() {
        val a = Texture(8, 8)
        val order = SpriteOrder()
        order.begin()
        order.add(Int.MIN_VALUE, a)
        order.addGroup(order.groupFor(Int.MAX_VALUE, a, DrawSpace.BACKGROUND))
        order.addGroup(order.groupFor(Int.MIN_VALUE, a, DrawSpace.SCREEN))
        order.addGroup(order.groupFor(-100, a, DrawSpace.BACKGROUND))
        order.finish()
        // Background by layer: sprite 3 then sprite 1; then the world's sprite 0; then the screen's sprite 2.
        assertEquals(listOf(2, 1, 3, 0), (0 until 4).map { order.claimPosition(it) })
        // One texture, but the spaces never merge into one run: the camera changes between them.
        assertEquals(listOf(0 to 2, 2 to 1, 3 to 1), (0 until order.runCount).map { order.runFirst(it) to order.runSize(it) })
        assertEquals(1, order.firstWorldRun)
        assertEquals(2, order.firstScreenRun)
    }

    @Test
    fun theThreeSpacesMixedKeepTheirRunsApartAndMarkTheProjectionSwitches() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val order = SpriteOrder()
        order.begin()
        // (space, layer, texture) in add order. The last background run and the first world run share
        // texture b, and the last world run and the first screen run share texture a.
        val sprites = listOf(
            Triple(DrawSpace.BACKGROUND, 0, a),
            Triple(DrawSpace.WORLD, 0, b),
            Triple(DrawSpace.BACKGROUND, 5, b),
            Triple(DrawSpace.SCREEN, 0, b),
            Triple(DrawSpace.WORLD, 1, a),
            Triple(DrawSpace.SCREEN, 0, a),
            Triple(DrawSpace.BACKGROUND, 5, a),
        )
        for ((space, layer, texture) in sprites) order.addGroup(order.groupFor(layer, texture, space))
        order.finish()
        assertEquals(listOf(0, 3, 2, 6, 4, 5, 1), sprites.indices.map { order.claimPosition(it) })
        val runs = (0 until order.runCount).map { Triple(order.runTexture(it), order.runFirst(it), order.runSize(it)) }
        assertEquals(
            listOf(Triple(a, 0, 2), Triple(b, 2, 1), Triple(b, 3, 1), Triple(a, 4, 1), Triple(a, 5, 1), Triple(b, 6, 1)),
            runs,
        )
        // Runs 0 and 1 draw with the screen projection, 2 and 3 through the camera, 4 and 5 with the screen again.
        assertEquals(2, order.firstWorldRun)
        assertEquals(4, order.firstScreenRun)
    }

    @Test
    fun theProjectionSwitchesLandWhereTheSpacesBeginWhicheverSpacesAreMissing() {
        val a = Texture(8, 8)
        val order = SpriteOrder()
        fun switches(vararg spaces: DrawSpace): Triple<Int, Int, Int> {
            order.begin()
            for (space in spaces) order.addGroup(order.groupFor(0, a, space))
            order.finish()
            return Triple(order.firstWorldRun, order.firstScreenRun, order.runCount)
        }
        assertEquals(Triple(0, 0, 0), switches())
        assertEquals(Triple(0, 1, 1), switches(DrawSpace.WORLD))
        assertEquals(Triple(1, 1, 1), switches(DrawSpace.BACKGROUND))
        assertEquals(Triple(0, 0, 1), switches(DrawSpace.SCREEN))
        assertEquals(Triple(1, 2, 2), switches(DrawSpace.BACKGROUND, DrawSpace.WORLD))
        assertEquals(Triple(1, 1, 2), switches(DrawSpace.BACKGROUND, DrawSpace.SCREEN))
        assertEquals(Triple(0, 1, 2), switches(DrawSpace.WORLD, DrawSpace.SCREEN))
        assertEquals(Triple(1, 2, 3), switches(DrawSpace.SCREEN, DrawSpace.WORLD, DrawSpace.BACKGROUND))
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

        order.begin()
        order.addGroup(order.groupFor(0, a, DrawSpace.BACKGROUND))
        order.add(0, a)
        order.finish()
        assertEquals(1, order.firstWorldRun)
        assertEquals(2, order.firstScreenRun)
        assertEquals(2, order.runCount)
    }

    @Test
    fun backgroundWorldAndScreenGroupsOfOneLayerAndTextureStayApartAndArePredicted() {
        val a = Texture(8, 8)
        val order = SpriteOrder()
        order.begin()
        order.addGroupWithoutPosition(order.groupFor(0, a, DrawSpace.SCREEN))
        order.addWithoutPosition(0, a)
        order.addGroupWithoutPosition(order.groupFor(0, a, DrawSpace.BACKGROUND))
        order.finish()
        assertFalse(order.isSorted)
        assertEquals(3, order.runCount)

        // The same groups again: place predicts all three, the background group first.
        order.begin()
        val placed = listOf(
            order.placeGroup(order.groupFor(0, a, DrawSpace.SCREEN)),
            order.place(0, a),
            order.placeGroup(order.groupFor(0, a, DrawSpace.BACKGROUND)),
        )
        order.finish()
        assertTrue(order.predictionHeld)
        assertEquals(listOf(2, 1, 0), placed)
        assertEquals(1, order.firstWorldRun)
        assertEquals(2, order.firstScreenRun)
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
