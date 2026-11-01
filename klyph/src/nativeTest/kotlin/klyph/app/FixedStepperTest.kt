package klyph.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class FixedStepperTest {
    @Test
    fun frameOfOneStepGivesOneStepPerFrame() {
        val stepper = FixedStepper(1.seconds / 60, maxStepsPerFrame = 5)
        repeat(60) { assertEquals(1, stepper.accumulate(1.seconds / 60)) }
    }

    @Test
    fun frames16point6MillisecondsAt60HzAverageOneStepPerFrame() {
        val stepper = FixedStepper(1.seconds / 60, maxStepsPerFrame = 5)
        var total = 0
        repeat(60) {
            val steps = stepper.accumulate(16.6.milliseconds)
            assertTrue(steps in 0..1, "Frame $it ran $steps steps.")
            total += steps
        }
        // 60 frames of 16.6 ms cover 996 ms, which holds 59 whole steps of 16.67 ms.
        assertEquals(59, total)
    }

    @Test
    fun longFrameRunsMaxStepsAndDiscardsTheRest() {
        val stepper = FixedStepper(1.seconds / 60, maxStepsPerFrame = 5)
        assertEquals(5, stepper.accumulate(1.seconds))
        assertEquals(0, stepper.accumulate(Duration.ZERO))
        assertEquals(1, stepper.accumulate(1.seconds / 60))
    }

    @Test
    fun alphaIsZeroBeforeTheFirstFrame() {
        assertEquals(0f, FixedStepper(20.milliseconds, maxStepsPerFrame = 5).alpha)
    }

    @Test
    fun alphaIsTheLeftoverFractionOfAStep() {
        val stepper = FixedStepper(20.milliseconds, maxStepsPerFrame = 5)
        assertEquals(0, stepper.accumulate(5.milliseconds))
        assertEquals(0.25f, stepper.alpha)
        assertEquals(1, stepper.accumulate(25.milliseconds))
        assertEquals(0.5f, stepper.alpha)
        assertEquals(1, stepper.accumulate(10.milliseconds))
        assertEquals(0f, stepper.alpha)
    }

    @Test
    fun alphaKeepsTheLeftoverWhenStepsAreDiscarded() {
        val stepper = FixedStepper(20.milliseconds, maxStepsPerFrame = 2)
        assertEquals(2, stepper.accumulate(105.milliseconds))
        assertEquals(0.25f, stepper.alpha)
    }

    @Test
    fun alphaStaysBelowOneWhenTheLeftoverRoundsUp() {
        val stepper = FixedStepper(1.seconds, maxStepsPerFrame = 1)
        // 999,999,999 / 1,000,000,000 rounds to 1.0 as a Float.
        assertEquals(0, stepper.accumulate(1.seconds - 1.nanoseconds))
        assertTrue(stepper.alpha < 1f, "Alpha ${stepper.alpha} is not below 1.")
        assertTrue(stepper.alpha > 0.99f, "Alpha ${stepper.alpha} lost the leftover.")
    }

    @Test
    fun rejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { FixedStepper(Duration.ZERO, maxStepsPerFrame = 1) }
        assertFailsWith<IllegalArgumentException> { FixedStepper((-1).milliseconds, maxStepsPerFrame = 1) }
        assertFailsWith<IllegalArgumentException> { FixedStepper(16.milliseconds, maxStepsPerFrame = 0) }
        val stepper = FixedStepper(16.milliseconds, maxStepsPerFrame = 1)
        assertFailsWith<IllegalArgumentException> { stepper.accumulate((-1).milliseconds) }
    }
}
