package klyph.collision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollisionLayersTest {
    @Test
    fun ofSetsOneBit() {
        assertEquals(1, CollisionLayers.of(0).bits)
        assertEquals(1 shl 5, CollisionLayers.of(5).bits)
        assertEquals(Int.MIN_VALUE, CollisionLayers.of(31).bits)
    }

    @Test
    fun ofRejectsIndexOutsideRange() {
        val error = assertFailsWith<IllegalArgumentException> { CollisionLayers.of(32) }
        assertEquals("Collision layer 32 is not between 0 and 31.", error.message)
        assertFailsWith<IllegalArgumentException> { CollisionLayers.of(-1) }
    }

    @Test
    fun constants() {
        assertEquals(CollisionLayers.of(0), CollisionLayers.DEFAULT)
        assertEquals(-1, CollisionLayers.ALL.bits)
        assertEquals(0, CollisionLayers.NONE.bits)
    }

    @Test
    fun plusIsUnion() {
        val set = CollisionLayers.of(1) + CollisionLayers.of(3) + CollisionLayers.of(1)
        assertEquals(0b1010, set.bits)
        assertEquals(CollisionLayers.ALL, CollisionLayers.ALL + CollisionLayers.of(4))
        assertEquals(CollisionLayers.of(2), CollisionLayers.NONE + CollisionLayers.of(2))
    }

    @Test
    fun containsMeansAnyLayerInCommon() {
        val set = CollisionLayers.of(1) + CollisionLayers.of(3)
        assertTrue(set.contains(CollisionLayers.of(1)))
        assertFalse(set.contains(CollisionLayers.of(2)))
        assertTrue(set.contains(CollisionLayers.of(2) + CollisionLayers.of(3)))
        assertTrue(CollisionLayers.ALL.contains(CollisionLayers.of(31)))
        assertFalse(CollisionLayers.ALL.contains(CollisionLayers.NONE))
        assertFalse(CollisionLayers.NONE.contains(CollisionLayers.ALL))
    }

    @Test
    fun toStringListsLayerNumbers() {
        assertEquals("CollisionLayers(0, 2)", (CollisionLayers.of(0) + CollisionLayers.of(2)).toString())
        assertEquals("CollisionLayers()", CollisionLayers.NONE.toString())
        assertEquals("CollisionLayers(31)", CollisionLayers.of(31).toString())
        assertEquals((0..31).joinToString(prefix = "CollisionLayers(", postfix = ")"), CollisionLayers.ALL.toString())
    }
}
