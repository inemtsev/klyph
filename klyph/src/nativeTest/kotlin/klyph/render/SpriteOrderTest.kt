package klyph.render

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpriteOrderTest {
    /** Runs one frame of [sprites] as (layer, texture) pairs and returns each sprite's position. */
    private fun SpriteOrder.frame(vararg sprites: Pair<Int, Texture>): List<Int> {
        begin()
        for ((layer, texture) in sprites) add(layer, texture)
        finish()
        return sprites.indices.map { claimPosition(it) }
    }

    /** Returns the runs as (texture, first, size) triples. */
    private fun SpriteOrder.runs(): List<Triple<Texture, Int, Int>> =
        (0 until runCount).map { Triple(runTexture(it), runFirst(it), runSize(it)) }

    @Test
    fun oneLayerAndTextureKeepsStorageOrderInOneRun() {
        val a = Texture(8, 8)
        val order = SpriteOrder()
        assertEquals(listOf(0, 1, 2), order.frame(0 to a, 0 to a, 0 to a))
        assertEquals(listOf(Triple(a, 0, 3)), order.runs())
    }

    @Test
    fun isSortedOnlyWhenEveryGroupIsOneStretchInSortedOrder() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val order = SpriteOrder()
        order.frame(0 to a, 0 to a, 0 to b, 1 to a, 1 to a)
        assertTrue(order.isSorted)
        order.frame(0 to a, 0 to b, 0 to a)
        assertFalse(order.isSorted)
        order.frame(1 to a, 0 to a)
        assertFalse(order.isSorted)
        order.frame()
        assertTrue(order.isSorted)
    }

    @Test
    fun addWithoutPositionGivesTheSameRunsAndSortedness() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val sprites = listOf(1 to a, 0 to b, 0 to a, 1 to a, 0 to b, 2 to b)
        val keyed = SpriteOrder()
        keyed.frame(*sprites.toTypedArray())
        val unkeyed = SpriteOrder()
        unkeyed.begin()
        for ((layer, texture) in sprites) unkeyed.addWithoutPosition(layer, texture)
        unkeyed.finish()
        assertEquals(keyed.runs(), unkeyed.runs())
        assertFalse(unkeyed.isSorted)

        unkeyed.begin()
        for ((layer, texture) in listOf(0 to a, 0 to a, 1 to b)) unkeyed.addWithoutPosition(layer, texture)
        unkeyed.finish()
        assertTrue(unkeyed.isSorted)
        assertEquals(listOf(Triple(a, 0, 2), Triple(b, 2, 1)), unkeyed.runs())
    }

    @Test
    fun placeReturnsTheSortedPositionWhileGroupsRepeat() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val sprites = listOf(1 to a, 0 to b, 0 to a, 1 to a, 0 to b)
        val order = SpriteOrder()
        val sorted = order.frame(*sprites.toTypedArray())
        assertFalse(order.predictionHeld)

        order.begin()
        val placed = sprites.map { (layer, texture) -> order.place(layer, texture) }
        order.finish()
        assertTrue(order.predictionHeld)
        assertEquals(sorted, placed)

        // One more sprite of an existing group changes the sizes.
        order.begin()
        for ((layer, texture) in sprites + (0 to a)) order.place(layer, texture)
        order.finish()
        assertFalse(order.predictionHeld)

        // A group the previous frame lacked is placed out of range.
        order.begin()
        val unplaced = (sprites + (0 to a) + (3 to b)).map { (layer, texture) -> order.place(layer, texture) }
        order.finish()
        assertFalse(order.predictionHeld)
        assertTrue(unplaced.last() >= SpriteOrder.UNPLACED)
    }

    @Test
    fun texturesGroupInFirstSeenOrderAndStayStable() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val order = SpriteOrder()
        assertEquals(listOf(0, 3, 1, 4, 2), order.frame(0 to a, 0 to b, 0 to a, 0 to b, 0 to a))
        assertEquals(listOf(Triple(a, 0, 3), Triple(b, 3, 2)), order.runs())
    }

    @Test
    fun lowerLayersComeFirstIncludingNegativeOnes() {
        val a = Texture(8, 8)
        val order = SpriteOrder()
        assertEquals(listOf(3, 0, 2, 1), order.frame(5 to a, -3 to a, 1 to a, 0 to a))
        // One texture throughout, so the four layers merge into one run.
        assertEquals(listOf(Triple(a, 0, 4)), order.runs())
    }

    @Test
    fun layerWinsOverTextureAndAdjacentGroupsOfOneTextureMerge() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val order = SpriteOrder()
        // Groups in order: (0, a) = sprites 0 and 4, (0, b) = sprite 2, (1, b) = sprites 1 and 3.
        assertEquals(listOf(0, 3, 2, 4, 1), order.frame(0 to a, 1 to b, 0 to b, 1 to b, 0 to a))
        assertEquals(listOf(Triple(a, 0, 2), Triple(b, 2, 3)), order.runs())
    }

    @Test
    fun textureSeenInOneLayerOnlyAfterAnotherDoesNotMerge() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val order = SpriteOrder()
        // Slots: a = 0, b = 1. Groups: (0, a), (0, b), (1, a), (1, b).
        assertEquals(listOf(3, 1, 0, 4, 2), order.frame(1 to a, 0 to b, 0 to a, 1 to b, 0 to b))
        assertEquals(listOf(Triple(a, 0, 1), Triple(b, 1, 2), Triple(a, 3, 1), Triple(b, 4, 1)), order.runs())
    }

    @Test
    fun framesDoNotLeakSlotsOrGroups() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val order = SpriteOrder()
        order.frame(0 to a, 2 to b, 1 to a)
        assertEquals(listOf(0, 1), order.frame(0 to b, 0 to b))
        assertEquals(listOf(Triple(b, 0, 2)), order.runs())
        order.begin()
        order.finish()
        assertEquals(0, order.runCount)
    }

    @Test
    fun twoOrdersOnTheSameFrameNumberDoNotShareSlots() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val first = SpriteOrder()
        val second = SpriteOrder()
        // Both orders are on their first frame, so only the owner tells their slots apart.
        first.frame(0 to a, 0 to b)
        second.frame(0 to b, 0 to a)
        assertEquals(listOf(Triple(b, 0, 1), Triple(a, 1, 1)), second.runs())
        assertEquals(listOf(0, 2, 1), first.frame(0 to a, 0 to b, 0 to a))
        assertEquals(listOf(Triple(a, 0, 2), Triple(b, 2, 1)), first.runs())
    }

    @Test
    fun twoOrdersCanBeBetweenBeginAndFinishAtOnce() {
        val a = Texture(8, 8)
        val b = Texture(8, 8)
        val first = SpriteOrder()
        val second = SpriteOrder()
        first.begin()
        second.begin()
        for (layer in listOf(1, 0, 1)) {
            first.add(layer, a)
            second.add(0, b)
            second.add(layer, a)
        }
        first.finish()
        second.finish()
        assertEquals(listOf(1, 0, 2), (0 until 3).map { first.claimPosition(it) })
        assertEquals(listOf(Triple(a, 0, 3)), first.runs())
        assertEquals(listOf(0, 4, 1, 3, 2, 5), (0 until 6).map { second.claimPosition(it) })
        assertEquals(listOf(Triple(b, 0, 3), Triple(a, 3, 3)), second.runs())
    }

    @Test
    fun textureIdsAreDistinctAndGrowWithCreationOrder() {
        val ids = List(100) { Texture(1, 1).id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.size, ids.toSet().size)
        // An id far beyond the initial per-texture capacity still sorts.
        val late = Texture(1, 1)
        val order = SpriteOrder()
        assertEquals(listOf(0, 1), order.frame(0 to late, 0 to late))
    }

    @Test
    fun manyGroupsSortLikeAStableSortByLayerThenTexture() {
        val textures = List(7) { Texture(8, 8) }
        val random = Random(7)
        val sprites = List(5000) { random.nextInt(-200, 200) to textures[random.nextInt(textures.size)] }
        val order = SpriteOrder()
        repeat(2) {
            val positions = order.frame(*sprites.toTypedArray())
            val slot = HashMap<Texture, Int>()
            for ((_, texture) in sprites) slot.getOrPut(texture) { slot.size }
            val expected = sprites.indices.sortedWith(compareBy({ sprites[it].first }, { slot.getValue(sprites[it].second) }))
            val actual = IntArray(sprites.size)
            positions.forEachIndexed { index, position -> actual[position] = index }
            assertEquals(expected, actual.toList())

            var next = 0
            for ((texture, first, size) in order.runs()) {
                assertEquals(next, first)
                assertTrue(size > 0)
                for (p in first until first + size) assertSame(texture, sprites[actual[p]].second)
                next += size
            }
            assertEquals(sprites.size, next)
        }
    }
}
