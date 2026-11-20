package klyph.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The frame loop needs a window, so these tests run the parts of it that turn the duration of a
 * frame into game time: the config's choice of delta, [Time.beginFrame], the [FixedStepper] and
 * the step counter, in the order `FrameLoop.frame` uses them.
 */
class FrameDeltaTest {
    /** What a run leaves behind: its clock, and how many fixed steps each frame ran. */
    private class Run(val time: Time, val stepsPerFrame: List<Int>)

    /** Runs one frame for each of [measured], the durations a real clock would have reported. */
    private fun runFrames(config: AppConfig, measured: List<Duration>): Run {
        val time = Time(config.fixedRate)
        val stepper = FixedStepper(time.fixedDelta, config.maxFixedStepsPerFrame)
        val steps = mutableListOf<Int>()
        for (duration in measured) {
            time.beginFrame(config.frameDeltaOf(duration))
            val count = stepper.accumulate(time.delta)
            repeat(count) {
                time.beginFixedStep()
                time.endFixedStep()
            }
            time.endFrame()
            steps += count
        }
        return Run(time, steps)
    }

    @Test
    fun aFrameDeltaMakesEveryFrameLastThatLong() {
        // Steps of 20 ms and frames of 30 ms: one step, then two, because 10 ms carry over.
        val real = listOf(3, 70, 16, 500, 0, 41).map { it.milliseconds }
        val run = runFrames(AppConfig(fixedRate = 50, frameDelta = 30.milliseconds), real)
        assertEquals(6L, run.time.frameCount)
        assertEquals(180.milliseconds, run.time.elapsed)
        assertEquals(9L, run.time.fixedStepCount)
        assertEquals(listOf(1, 2, 1, 2, 1, 2), run.stepsPerFrame)
    }

    @Test
    fun twoRunsWithDifferentRealTimingsMatchFrameForFrame() {
        val config = AppConfig(frameDelta = 16.milliseconds)
        val slow = runFrames(config, List(40) { (20 + it % 7 * 9).milliseconds })
        val fast = runFrames(config, List(40) { (1 + it % 3).milliseconds })
        assertEquals(16.milliseconds * 40, slow.time.elapsed)
        assertEquals(slow.time.elapsed, fast.time.elapsed)
        assertEquals(slow.time.fixedStepCount, fast.time.fixedStepCount)
        assertEquals(slow.stepsPerFrame, fast.stepsPerFrame)
    }

    @Test
    fun theLongestFrameDeltaRunsEveryStepItCoversAndDropsNone() {
        val config = AppConfig()
        val longest = Time(config.fixedRate).fixedDelta * config.maxFixedStepsPerFrame
        val run = runFrames(AppConfig(frameDelta = longest), List(10) { 1.milliseconds })
        assertEquals(List(10) { config.maxFixedStepsPerFrame }, run.stepsPerFrame)
        assertEquals(run.time.elapsed, run.time.fixedDelta * run.time.fixedStepCount.toInt(), "Every frame's time became steps.")
    }

    @Test
    fun withoutOneTheMeasuredDurationsAdvanceTimeAndTheClampStillApplies() {
        // The 500 ms stall counts as 250 ms, and the stepper runs at most four steps for it.
        val real = listOf(3, 70, 16, 500, 0, 41).map { it.milliseconds }
        val run = runFrames(AppConfig(fixedRate = 50), real)
        assertEquals(380.milliseconds, run.time.elapsed)
        assertEquals(listOf(0, 3, 1, 4, 0, 3), run.stepsPerFrame)
        assertEquals(11L, run.time.fixedStepCount)
    }

    @Test
    fun frameDeltaOfPicksTheConfiguredDeltaOverTheMeasuredOne() {
        assertEquals(16.milliseconds, AppConfig(frameDelta = 16.milliseconds).frameDeltaOf(measured = 33.milliseconds))
        assertEquals(33.milliseconds, AppConfig().frameDeltaOf(measured = 33.milliseconds))
        assertEquals(Duration.ZERO, AppConfig().frameDeltaOf(measured = Duration.ZERO))
    }
}
