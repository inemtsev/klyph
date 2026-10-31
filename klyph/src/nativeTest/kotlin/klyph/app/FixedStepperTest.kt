package klyph.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
    fun rejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { FixedStepper(Duration.ZERO, maxStepsPerFrame = 1) }
        assertFailsWith<IllegalArgumentException> { FixedStepper((-1).milliseconds, maxStepsPerFrame = 1) }
        assertFailsWith<IllegalArgumentException> { FixedStepper(16.milliseconds, maxStepsPerFrame = 0) }
        val stepper = FixedStepper(16.milliseconds, maxStepsPerFrame = 1)
        assertFailsWith<IllegalArgumentException> { stepper.accumulate((-1).milliseconds) }
    }
}
