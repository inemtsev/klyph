package klyph.scene

import klyph.app.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SceneClockTest {
    @Test
    fun advanceClockAddsToTheTopSceneAndRejectsANegativeDelta() {
        val h = SceneHarness()
        val bottom = h.scene("bottom")
        val top = h.scene("top")
        h.stack.advanceClock(1.seconds)
        h.stack.push(bottom)
        h.stack.push(top)
        h.applyTransitions()

        h.stack.advanceClock(1.seconds)
        h.stack.advanceClock(500.milliseconds)
        assertEquals(Duration.ZERO, bottom.elapsed)
        assertEquals(1500.milliseconds, top.elapsed)
        assertFailsWith<IllegalArgumentException> { h.stack.advanceClock((-1).milliseconds) }
        assertEquals(1500.milliseconds, top.elapsed)
    }

    @Test
    fun sceneThatLeftKeepsItsElapsedUntilItEntersAgainAtZero() {
        val h = SceneHarness()
        val play = h.scene("play")
        assertEquals(Duration.ZERO, play.elapsed)
        h.stack.push(play)
        h.applyTransitions()
        h.stack.advanceClock(2.seconds)
        assertEquals(2.seconds, play.elapsed)

        h.stack.pop()
        h.applyTransitions()
        h.stack.advanceClock(1.seconds)
        assertEquals(2.seconds, play.elapsed, "A scene that left the stack keeps its last value.")

        h.stack.push(play)
        h.applyTransitions()
        assertEquals(Duration.ZERO, play.elapsed, "A scene that enters again starts over.")
        h.stack.advanceClock(500.milliseconds)
        assertEquals(500.milliseconds, play.elapsed)
    }

    @Test
    fun replaceStartsTheNewSceneAtZeroAndLeavesTheOthersTheirValues() {
        val h = SceneHarness()
        val a = h.scene("a")
        val b = h.scene("b")
        val c = h.scene("c")
        h.stack.push(a)
        h.applyTransitions()
        h.stack.advanceClock(1.seconds)
        h.stack.push(b)
        h.applyTransitions()
        h.stack.advanceClock(2.seconds)

        h.stack.replace(c)
        h.applyTransitions()
        assertEquals(1.seconds, a.elapsed)
        assertEquals(2.seconds, b.elapsed)
        assertEquals(Duration.ZERO, c.elapsed)
        h.stack.advanceClock(3.seconds)
        assertEquals(3.seconds, c.elapsed)
        assertEquals(1.seconds, a.elapsed)
    }

    @Test
    fun replaceOfASceneThatIsOnTheStackStartsItOver() {
        val h = SceneHarness()
        val a = h.scene("a")
        val b = h.scene("b")
        h.stack.push(a)
        h.applyTransitions()
        h.stack.advanceClock(3.seconds)
        h.stack.push(b)
        h.applyTransitions()
        h.stack.advanceClock(1.seconds)

        h.stack.replace(a)
        h.applyTransitions()
        assertEquals(Duration.ZERO, a.elapsed, "A frozen scene starts over when replace enters it again.")
        assertEquals(1.seconds, b.elapsed)
        h.stack.advanceClock(250.milliseconds)
        assertEquals(250.milliseconds, a.elapsed)
        assertEquals(1.seconds, b.elapsed)
    }

    @Test
    fun enterActionSeesTheClockAtZero() {
        val h = SceneHarness()
        val seen = mutableListOf<Duration>()
        lateinit var play: Scene
        play = h.scene("play", enter = { seen += play.elapsed })
        h.stack.push(play)
        h.applyTransitions()
        h.stack.advanceClock(2.seconds)

        h.stack.pop()
        h.stack.push(play)
        h.applyTransitions()
        assertEquals(listOf(Duration.ZERO, Duration.ZERO), seen)
    }

    @Test
    fun throwingEnterGivesTheSceneItsOldElapsedBack() {
        val h = SceneHarness()
        var fails = false
        var seenByAction: Duration? = null
        lateinit var play: Scene
        play = h.scene("play", enter = {
            seenByAction = play.elapsed
            if (fails) error("boom")
        })
        h.stack.push(play)
        h.applyTransitions()
        h.stack.advanceClock(4.seconds)
        h.stack.pop()
        h.applyTransitions()

        fails = true
        h.stack.push(play)
        assertFailsWith<IllegalStateException> { h.applyTransitions() }
        assertEquals(Duration.ZERO, seenByAction, "The action ran with the clock started over.")
        assertEquals(4.seconds, play.elapsed, "A failed entry leaves no trace, so the scene keeps its last value.")
        assertFalse(play in h.stack)

        val broken = h.scene("broken", enter = { error("boom") })
        h.stack.push(broken)
        assertFailsWith<IllegalStateException> { h.applyTransitions() }
        assertEquals(Duration.ZERO, broken.elapsed, "A scene that never entered stays at zero.")
    }

    @Test
    fun elapsedAdvancesByTheFrameDurationNotByTheFixedStep() {
        val h = SceneHarness()
        val inInput = mutableListOf<Duration>()
        val inFixedStep = mutableListOf<Duration>()
        val stepDeltas = mutableListOf<Duration>()
        val inUpdate = mutableListOf<Duration>()
        lateinit var play: Scene
        play = h.scene("play") {
            addSystem(Phase.INPUT, h.system { inInput += play.elapsed })
            addSystem(Phase.FIXED_UPDATE, h.system {
                inFixedStep += play.elapsed
                stepDeltas += h.time.delta
            })
            addSystem(Phase.UPDATE, h.system { inUpdate += play.elapsed })
        }
        h.stack.push(play)
        h.applyTransitions()

        // At 60 steps a second, 10 ms covers no step, and 25 ms and 40 ms cover two each.
        assertEquals(listOf(0, 2, 2), listOf(10, 25, 40).map { h.frame(it.milliseconds) })
        assertEquals(listOf(10.milliseconds, 35.milliseconds, 75.milliseconds), inInput, "The frame in progress counts.")
        assertEquals(inInput, inUpdate)
        assertEquals(listOf(35, 35, 75, 75).map { it.milliseconds }, inFixedStep, "Every step of a frame reads the same value.")
        assertEquals(List(4) { h.time.fixedDelta }, stepDeltas, "Time.delta was the fixed step inside the steps.")
        assertEquals(75.milliseconds, play.elapsed)

        h.frame(2.seconds)
        assertEquals(325.milliseconds, play.elapsed, "A long frame counts for at most 250 ms.")
        assertEquals(h.time.elapsed, play.elapsed)
    }
}
