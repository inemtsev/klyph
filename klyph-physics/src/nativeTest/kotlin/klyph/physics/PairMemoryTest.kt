package klyph.physics

import kotlin.test.Test
import kotlin.test.assertEquals

class PairMemoryTest {
    private val memory = PairMemory()

    @Test
    fun aPairAddedInOneSubstepIsRecalledInTheNextWithWhatWasNoted() {
        memory.forgetAll()
        memory.nextSubstep()
        memory.add(3, 7, firstContact = 10, contacts = 2, relX = 1.5f, relY = -2f, relTurn = 0.25f)
        memory.nextSubstep()
        val k = memory.recall(3, 7)
        assertEquals(10, memory.firstContactOf(k))
        assertEquals(2, memory.contactsOf(k))
        assertEquals(1.5f, memory.relXOf(k))
        assertEquals(-2f, memory.relYOf(k))
        assertEquals(0.25f, memory.relTurnOf(k))
    }

    @Test
    fun aPairIsFoundByItsBodiesInOrderAndNotByAnyOtherPair() {
        memory.nextSubstep()
        memory.add(1, 2, 0, 1, 0f, 0f, 0f)
        memory.nextSubstep()
        assertEquals(-1, memory.recall(2, 1))
        assertEquals(-1, memory.recall(1, 3))
        assertEquals(0, memory.recall(1, 2))
    }

    @Test
    fun nothingIsRecalledInTheSubstepAfterThePairWasAddedButNotTheOneAfterThat() {
        memory.nextSubstep()
        memory.add(1, 2, 0, 1, 0f, 0f, 0f)
        memory.nextSubstep()
        assertEquals(0, memory.recall(1, 2))
        memory.nextSubstep()
        assertEquals(-1, memory.recall(1, 2), "a pair not found again in a substep is forgotten")
    }

    @Test
    fun aNewStepForgetsEveryPairEvenOneJustAdded() {
        memory.nextSubstep()
        memory.add(1, 2, 0, 1, 0f, 0f, 0f)
        memory.forgetAll()
        memory.nextSubstep()
        assertEquals(-1, memory.recall(1, 2))
    }

    @Test
    fun morePairsThanItStartedWithAreAllKept() {
        memory.nextSubstep()
        val pairs = 1000
        for (i in 0 until pairs) memory.add(i, i + 1, firstContact = 2 * i, contacts = 2, relX = i.toFloat(), relY = 0f, relTurn = 0f)
        memory.nextSubstep()
        for (i in 0 until pairs) {
            val k = memory.recall(i, i + 1)
            assertEquals(2 * i, memory.firstContactOf(k), "pair $i")
            assertEquals(i.toFloat(), memory.relXOf(k), "pair $i")
        }
    }
}
