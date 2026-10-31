package klyph.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class FrameStatsTest {
    @Test
    fun startsAtZero() {
        val stats = FrameStats()
        for (phase in Phase.entries) assertEquals(Duration.ZERO, stats.durationOf(phase))
        assertEquals(Duration.ZERO, stats.total)
        assertEquals(Duration.ZERO, stats.transformPropagation)
        assertEquals(Duration.ZERO, stats.targetAcquisition)
        assertEquals(Duration.ZERO, stats.spriteExtraction)
        assertEquals(Duration.ZERO, stats.spriteRecording)
        assertEquals(Duration.ZERO, stats.gpuWait)
        assertEquals(0, stats.fixedSteps)
        assertEquals(0, stats.spriteCount)
    }

    @Test
    fun toStringIsOneLine() {
        assertEquals(
            "FrameStats(total=0.000ms, input=0.000ms, fixedUpdate=0.000ms, update=0.000ms, lateUpdate=0.000ms, " +
                "transformPropagation=0.000ms, render=0.000ms, targetAcquisition=0.000ms, spriteExtraction=0.000ms, " +
                "spriteRecording=0.000ms, gpuWait=0.000ms, fixedSteps=0, spriteCount=0)",
            FrameStats().toString(),
        )
    }

    @Test
    fun valuesAppearOnlyOnceTheFrameIsPublished() {
        val stats = FrameStats()
        stats.add(Phase.UPDATE.ordinal, 2_000_000)
        stats.add(FrameStats.GPU_WAIT, 500)
        assertEquals(Duration.ZERO, stats.durationOf(Phase.UPDATE))

        stats.publish(totalNanos = 3_000_000, fixedSteps = 2, spriteCount = 10)
        assertEquals(2.milliseconds, stats.durationOf(Phase.UPDATE))
        assertEquals(500.nanoseconds, stats.gpuWait)
        assertEquals(3.milliseconds, stats.total)
        assertEquals(2, stats.fixedSteps)
        assertEquals(10, stats.spriteCount)
    }

    @Test
    fun eachFrameStartsFromZero() {
        val stats = FrameStats()
        stats.add(Phase.UPDATE.ordinal, 1_000)
        stats.add(Phase.UPDATE.ordinal, 1_000)
        stats.publish(totalNanos = 0, fixedSteps = 0, spriteCount = 0)
        assertEquals(2_000.nanoseconds, stats.durationOf(Phase.UPDATE))
        stats.publish(totalNanos = 0, fixedSteps = 0, spriteCount = 0)
        assertEquals(Duration.ZERO, stats.durationOf(Phase.UPDATE))
    }

    @Test
    fun startupDurationOutlivesFrames() {
        val stats = FrameStats()
        stats.recordStartup(7_000_000)
        stats.publish(totalNanos = 0, fixedSteps = 0, spriteCount = 0)
        assertEquals(7.milliseconds, stats.durationOf(Phase.STARTUP))
        assertEquals(Duration.ZERO, stats.durationOf(Phase.SHUTDOWN))
    }
}
