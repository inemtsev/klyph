package klyph.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// App needs a window, so these tests drive the schedule through forEach, which run is built on.
class ScheduleTest {
    @Test
    fun visitsSystemsOfAPhaseInRegistrationOrder() {
        val schedule = Schedule()
        val a = System {}
        val b = System {}
        val c = System {}
        schedule.add(Phase.UPDATE, a)
        schedule.add(Phase.UPDATE, b)
        schedule.add(Phase.UPDATE, c)
        val seen = mutableListOf<System>()
        schedule.forEach(Phase.UPDATE) { seen += it }
        assertEquals(listOf(a, b, c), seen)
    }

    @Test
    fun phasesAreIndependent() {
        val schedule = Schedule()
        val update = System {}
        val render = System {}
        schedule.add(Phase.UPDATE, update)
        schedule.add(Phase.RENDER, render)
        val seen = mutableListOf<System>()
        schedule.forEach(Phase.RENDER) { seen += it }
        assertEquals(listOf(render), seen)
        schedule.forEach(Phase.INPUT) { seen += it }
        assertEquals(listOf(render), seen)
        schedule.forEach(Phase.UPDATE) { seen += it }
        assertEquals(listOf(render, update), seen)
    }

    @Test
    fun addingToTheRunningPhaseThrows() {
        val schedule = Schedule()
        schedule.add(Phase.UPDATE) {}
        assertFailsWith<IllegalStateException> { schedule.forEach(Phase.UPDATE) { schedule.add(Phase.UPDATE) {} } }
        assertEquals(1, schedule.count(Phase.UPDATE))
    }

    @Test
    fun addingToAnotherPhaseWhileRunningIsAllowed() {
        val schedule = Schedule()
        schedule.add(Phase.STARTUP) {}
        schedule.forEach(Phase.STARTUP) { schedule.add(Phase.UPDATE) {} }
        assertEquals(1, schedule.count(Phase.UPDATE))
    }

    @Test
    fun phaseIsAddableAgainAfterASystemThrows() {
        val schedule = Schedule()
        schedule.add(Phase.UPDATE) {}
        assertFailsWith<IllegalStateException> { schedule.forEach(Phase.UPDATE) { error("boom") } }
        schedule.add(Phase.UPDATE) {}
        assertEquals(2, schedule.count(Phase.UPDATE))
    }

    @Test
    fun exceptionsPropagateAndStopThePhase() {
        val schedule = Schedule()
        schedule.add(Phase.UPDATE) {}
        schedule.add(Phase.UPDATE) {}
        var visited = 0
        assertFailsWith<UnsupportedOperationException> {
            schedule.forEach(Phase.UPDATE) {
                visited++
                throw UnsupportedOperationException("boom")
            }
        }
        assertEquals(1, visited)
    }

    @Test
    fun countReportsSystemsPerPhase() {
        val schedule = Schedule()
        for (phase in Phase.entries) assertEquals(0, schedule.count(phase))
        schedule.add(Phase.FIXED_UPDATE) {}
        schedule.add(Phase.FIXED_UPDATE) {}
        schedule.add(Phase.LATE_UPDATE) {}
        assertEquals(2, schedule.count(Phase.FIXED_UPDATE))
        assertEquals(1, schedule.count(Phase.LATE_UPDATE))
        assertEquals(0, schedule.count(Phase.UPDATE))
    }
}
