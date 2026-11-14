package klyph.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimeTest {
    @Test
    fun fixedDeltaIsOneSecondOverRate() {
        assertEquals(1.seconds / 60, Time(60).fixedDelta)
        assertEquals(20.milliseconds, Time(50).fixedDelta)
    }

    @Test
    fun rejectsNonPositiveRate() {
        assertFailsWith<IllegalArgumentException> { Time(0) }
        assertFailsWith<IllegalArgumentException> { Time(-1) }
    }

    @Test
    fun startsAtZero() {
        val time = Time(60)
        assertEquals(Duration.ZERO, time.delta)
        assertEquals(Duration.ZERO, time.elapsed)
        assertEquals(0L, time.frameCount)
        assertEquals(0f, time.deltaSeconds)
        assertEquals(0f, time.fixedStepAlpha)
    }

    @Test
    fun fixedStepAlphaIsSetByTheLoopAndKeptInsideFixedSteps() {
        val time = Time(60)
        time.beginFrame(16.milliseconds)
        time.fixedStepAlpha = 0.75f
        time.beginFixedStep()
        assertEquals(0.75f, time.fixedStepAlpha)
        time.endFixedStep()
        assertEquals(0.75f, time.fixedStepAlpha)
    }

    @Test
    fun fixedStepAlphaRejectsValuesOutsideZeroUntilOne() {
        val time = Time(60)
        assertFailsWith<IllegalArgumentException> { time.fixedStepAlpha = 1f }
        assertFailsWith<IllegalArgumentException> { time.fixedStepAlpha = -0.1f }
        assertFailsWith<IllegalArgumentException> { time.fixedStepAlpha = Float.NaN }
        assertEquals(0f, time.fixedStepAlpha)
    }

    @Test
    fun beginFrameClampsDeltaTo250Milliseconds() {
        val time = Time(60)
        time.beginFrame(2.seconds)
        assertEquals(250.milliseconds, time.delta)
        assertEquals(250.milliseconds, time.elapsed)
        assertEquals(0.25f, time.deltaSeconds)
    }

    @Test
    fun beginFrameClampsNegativeDeltaToZero() {
        val time = Time(60)
        time.beginFrame((-5).milliseconds)
        assertEquals(Duration.ZERO, time.delta)
        assertEquals(Duration.ZERO, time.elapsed)
    }

    @Test
    fun elapsedAccumulatesFrameDeltas() {
        val time = Time(60)
        time.beginFrame(10.milliseconds)
        time.endFrame()
        time.beginFrame(20.milliseconds)
        time.endFrame()
        time.beginFrame(1.seconds)
        assertEquals(280.milliseconds, time.elapsed)
        assertEquals(250.milliseconds, time.delta)
    }

    @Test
    fun frameIncrementsOnEndFrame() {
        val time = Time(60)
        time.beginFrame(16.milliseconds)
        assertEquals(0L, time.frameCount)
        time.endFrame()
        assertEquals(1L, time.frameCount)
        time.beginFrame(16.milliseconds)
        assertEquals(1L, time.frameCount)
        time.endFrame()
        assertEquals(2L, time.frameCount)
    }

    @Test
    fun fixedStepCountGoesUpAtTheEndOfEveryStep() {
        val time = Time(60)
        time.beginFrame(40.milliseconds)
        assertEquals(0L, time.fixedStepCount)
        time.beginFixedStep()
        assertEquals(0L, time.fixedStepCount)
        time.endFixedStep()
        time.beginFixedStep()
        time.endFixedStep()
        assertEquals(2L, time.fixedStepCount)
        time.endFrame()
        time.beginFrame(1.milliseconds)
        time.endFrame()
        assertEquals(2L, time.fixedStepCount)
    }

    @Test
    fun deltaSwitchesToFixedDeltaInsideAFixedStep() {
        val time = Time(50)
        time.beginFrame(33.milliseconds)
        assertEquals(33.milliseconds, time.delta)
        time.beginFixedStep()
        assertEquals(20.milliseconds, time.delta)
        assertEquals(0.02f, time.deltaSeconds)
        assertEquals(33.milliseconds, time.elapsed)
        time.endFixedStep()
        assertEquals(33.milliseconds, time.delta)
        assertEquals(0.033f, time.deltaSeconds)
    }

    @Test
    fun fixedDeltaSecondsIsTheStepLengthInEveryPhase() {
        val time = Time(50)
        assertEquals(0.02f, time.fixedDeltaSeconds)
        time.beginFrame(33.milliseconds)
        assertEquals(0.02f, time.fixedDeltaSeconds, "Outside a fixed step, where deltaSeconds is the frame.")
        time.beginFixedStep()
        assertEquals(0.02f, time.fixedDeltaSeconds)
        time.endFixedStep()
        assertEquals(0.02f, time.fixedDeltaSeconds)
    }

    @Test
    fun elapsedSecondsIsElapsedInSeconds() {
        val time = Time(60)
        assertEquals(0f, time.elapsedSeconds)
        time.beginFrame(100.milliseconds)
        time.endFrame()
        time.beginFrame(150.milliseconds)
        assertEquals(0.25f, time.elapsedSeconds)
        time.endFrame()
        time.beginFrame(2.seconds)
        assertEquals(0.5f, time.elapsedSeconds, "A long frame counts for its clamped 250 milliseconds.")
    }

    @Test
    fun fixedStepCallsMustPair() {
        val time = Time(60)
        assertFailsWith<IllegalStateException> { time.endFixedStep() }
        time.beginFixedStep()
        assertFailsWith<IllegalStateException> { time.beginFixedStep() }
        assertFailsWith<IllegalStateException> { time.endFrame() }
        assertFailsWith<IllegalStateException> { time.beginFrame(1.milliseconds) }
        time.endFixedStep()
    }

    @Test
    fun toStringShowsFrameElapsedAndDelta() {
        val time = Time(60)
        repeat(12) {
            time.beginFrame(16.6.milliseconds)
            time.endFrame()
        }
        time.beginFrame(16.6.milliseconds)
        assertEquals("Time(frameCount=12, elapsed=0.2s, delta=16.6ms)", time.toString())
    }
}
