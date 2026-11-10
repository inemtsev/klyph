package klyph.input

import klyph.app.FixedStepper
import klyph.app.Time
import klyph.platform.KeyCode
import klyph.platform.PlatformEvent
import klyph.platform.Scancode
import klyph.render.Camera
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** "Just pressed" and "just released" inside FIXED_UPDATE, where they count fixed steps, not frames. */
class FixedStepInputTest {
    private val clock = StepClock()
    private val buttons = ButtonStates(4, clock)

    /** Starts a frame as `Input.beginFrame` does, then applies [changes] as its platform events. */
    private fun frame(changes: ButtonStates.() -> Unit = {}) {
        buttons.clearEdges()
        buttons.changes()
    }

    /** Runs one fixed step and returns what button 0's edges read inside it. */
    private fun step(): Pair<Boolean, Boolean> {
        clock.beginFixedStep()
        val edges = buttons.isJustPressed(0) to buttons.isJustReleased(0)
        clock.endFixedStep()
        return edges
    }

    /** Runs [steps] fixed steps and returns how many saw button 0 just pressed. */
    private fun pressHits(steps: Int): Int {
        var hits = 0
        repeat(steps) { if (step().first) hits++ }
        return hits
    }

    @Test
    fun renderingAtTwiceTheStepRateSeesEachPressOnce() {
        // Frames alternate between no step and one step, as at 120 Hz with 60 steps a second. A
        // press every fifth frame lands in frames with a step and in frames without, by turns.
        var hits = 0
        for (frameIndex in 0 until 40) {
            frame {
                if (frameIndex % 5 == 0) press(0)
                if (frameIndex % 5 == 2) release(0)
            }
            if (frameIndex % 2 == 1) hits += pressHits(1)
        }
        assertEquals(8, hits)
    }

    @Test
    fun renderingAtHalfTheStepRateSeesEachPressOnce() {
        // Every frame runs two steps, as at 30 Hz with 60 steps a second.
        var hits = 0
        for (frameIndex in 0 until 20) {
            frame {
                if (frameIndex % 2 == 0) press(0) else release(0)
            }
            hits += pressHits(2)
        }
        assertEquals(10, hits)
    }

    @Test
    fun secondStepOfAFrameDoesNotSeeThePressAgain() {
        frame { press(0) }
        assertEquals(true to false, step())
        assertEquals(false to false, step())
        assertTrue(buttons.isDown(0))
    }

    @Test
    fun pressCarriesAcrossFramesThatRunNoStep() {
        frame { press(0) }
        frame()
        frame()
        assertEquals(true to false, step())
        assertEquals(false to false, step())
    }

    @Test
    fun releaseIsSeenByTheFirstStepAfterIt() {
        frame { press(0) }
        step()
        frame { release(0) }
        frame()
        assertEquals(false to true, step())
        assertEquals(false to false, step())
    }

    @Test
    fun tapBeforeAStepReadsAsPressedAndReleasedWithTheButtonUp() {
        frame {
            press(0)
            release(0)
        }
        clock.beginFixedStep()
        assertTrue(buttons.isJustPressed(0))
        assertTrue(buttons.isJustReleased(0))
        assertFalse(buttons.isDown(0))
        clock.endFixedStep()
    }

    @Test
    fun tapAcrossTwoFramesWithoutAStepReadsAsBoth() {
        frame { press(0) }
        frame { release(0) }
        assertEquals(true to true, step())
    }

    @Test
    fun frameEdgesOutsideFixedStepsAreUnchanged() {
        frame { press(0) }
        assertTrue(buttons.isJustPressed(0))
        step()
        // UPDATE in the same frame still sees the frame's press, after the step took it.
        assertTrue(buttons.isJustPressed(0))
        frame()
        assertFalse(buttons.isJustPressed(0))
        // A frame with no step: the press waits for a step, but UPDATE sees it now.
        frame { release(0) }
        assertTrue(buttons.isJustReleased(0))
        frame()
        assertFalse(buttons.isJustReleased(0))
        assertEquals(false to true, step())
    }

    @Test
    fun noStepSeesAPressBeforeTheFirstPress() {
        frame()
        assertEquals(false to false, step())
        assertFalse(buttons.isJustPressed(3))
    }

    @Test
    fun clearForgetsStepEdges() {
        frame { press(0) }
        buttons.clear()
        assertEquals(false to false, step())
    }

    @Test
    fun outOfRangeIndicesAreNeverJustPressedInAStep() {
        frame { press(0) }
        clock.beginFixedStep()
        assertFalse(buttons.isJustPressed(-1))
        assertFalse(buttons.isJustPressed(4))
        clock.endFixedStep()
    }

    @Test
    fun stepClockCallsMustPair() {
        assertFailsWith<IllegalStateException> { clock.endFixedStep() }
        clock.beginFixedStep()
        assertFailsWith<IllegalStateException> { clock.beginFixedStep() }
        clock.endFixedStep()
        assertEquals("StepClock(step=1, isInFixedStep=false)", clock.toString())
    }
}

/**
 * Drives [Input], [Time] and a [FixedStepper] in the order the frame loop does, with real frame
 * durations, since the loop itself needs a window.
 */
class FixedStepLoopTest {
    private val time = Time(FIXED_RATE)
    private val input = Input()
    private val stepper = FixedStepper(time.fixedDelta, maxStepsPerFrame = 5)
    private val camera = Camera(zoom = 1f, viewportWidth = 800f, viewportHeight = 600f)

    /** Hits a FIXED_UPDATE system saw: steps in which the space bar read as just pressed. */
    private var stepHits = 0

    /** Hits an UPDATE system saw: frames in which the space bar read as just pressed. */
    private var frameHits = 0

    /** Runs one frame of [delta] with [events], as `FrameLoop.frame` does, and returns its step count. */
    private fun frame(delta: Duration, vararg events: PlatformEvent): Int {
        time.beginFrame(delta)
        input.beginFrame()
        for (event in events) input.handle(event)
        input.endFrame(camera)
        val steps = stepper.accumulate(time.delta)
        repeat(steps) {
            time.beginFixedStep()
            input.beginFixedStep()
            if (input.keyboard.isJustPressed(Scancode.SPACE)) stepHits++
            input.endFixedStep()
            time.endFixedStep()
        }
        if (input.keyboard.isJustPressed(Scancode.SPACE)) frameHits++
        time.endFrame()
        return steps
    }

    /** Presses and releases the space bar [presses] times, a press every [period] frames of [delta]. */
    private fun play(delta: Duration, presses: Int, period: Int) {
        for (frameIndex in 0 until presses * period) {
            when (frameIndex % period) {
                0 -> frame(delta, keyDown(Scancode.SPACE))
                1 -> frame(delta, keyUp(Scancode.SPACE))
                else -> frame(delta)
            }
        }
        // Lets the last press reach a step.
        frame(delta)
        frame(delta)
    }

    private fun keyDown(scancode: Scancode) = PlatformEvent.KeyDown(KeyCode(0u), isRepeat = false, scancode = scancode)

    private fun keyUp(scancode: Scancode) = PlatformEvent.KeyUp(KeyCode(0u), scancode)

    @Test
    fun framesAtTwiceTheStepRateSeeEveryPressInExactlyOneStep() {
        assertEquals(listOf(0, 1, 0, 1), List(4) { frame(FRAME_AT_TWICE_RATE) })
        play(FRAME_AT_TWICE_RATE, presses = 15, period = 3)
        assertEquals(15, stepHits)
        assertEquals(15, frameHits)
    }

    @Test
    fun framesAtHalfTheStepRateSeeEveryPressInExactlyOneStep() {
        assertEquals(2, frame(FRAME_AT_HALF_RATE))
        play(FRAME_AT_HALF_RATE, presses = 15, period = 2)
        assertEquals(15, stepHits)
        assertEquals(15, frameHits)
        assertEquals(time.frameCount * 2, time.fixedStepCount)
    }

    @Test
    fun aTapInAFrameWithoutAStepIsStillSeen() {
        assertEquals(0, frame(FRAME_AT_TWICE_RATE, keyDown(Scancode.SPACE), keyUp(Scancode.SPACE)))
        assertEquals(1, frame(FRAME_AT_TWICE_RATE))
        assertEquals(1, stepHits)
        assertEquals(1, frameHits)
    }

    private companion object {
        const val FIXED_RATE = 50
        val FRAME_AT_TWICE_RATE: Duration = 10.milliseconds
        val FRAME_AT_HALF_RATE: Duration = 40.milliseconds
    }
}
