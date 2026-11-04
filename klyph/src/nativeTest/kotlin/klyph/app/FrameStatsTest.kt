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
        assertEquals(Duration.ZERO, stats.collisionDetection)
        assertEquals(0, stats.fixedStepCount)
        assertEquals(0, stats.spriteCount)
        assertEquals(0, stats.drawCallCount)
        assertEquals(0, stats.collisionPairCount)
        assertEquals(0, stats.entityCount)
        assertEquals(Duration.ZERO, stats.particleAdvance)
        assertEquals(0, stats.particleCount)
    }

    @Test
    fun toStringIsOneLine() {
        assertEquals(
            "FrameStats(total=0.000ms, input=0.000ms, fixedUpdate=0.000ms, collisionDetection=0.000ms, update=0.000ms, " +
                "lateUpdate=0.000ms, transformPropagation=0.000ms, render=0.000ms, targetAcquisition=0.000ms, " +
                "spriteExtraction=0.000ms, spriteRecording=0.000ms, gpuWait=0.000ms, fixedStepCount=0, spriteCount=0, drawCallCount=0, " +
                "collisionPairCount=0, entityCount=0, particleAdvance=0.000ms, particleCount=0)",
            FrameStats().toString(),
        )
    }

    @Test
    fun valuesAppearOnlyOnceTheFrameIsPublished() {
        val stats = FrameStats()
        stats.add(Phase.UPDATE.ordinal, 2_000_000)
        stats.add(FrameStats.GPU_WAIT, 500)
        stats.add(FrameStats.COLLISION, 700)
        stats.add(FrameStats.PARTICLES, 800)
        assertEquals(Duration.ZERO, stats.durationOf(Phase.UPDATE))

        stats.publish(totalNanos = 3_000_000, fixedStepCount = 2, spriteCount = 10, drawCallCount = 3, collisionPairCount = 4, particleCount = 5)
        assertEquals(2.milliseconds, stats.durationOf(Phase.UPDATE))
        assertEquals(500.nanoseconds, stats.gpuWait)
        assertEquals(700.nanoseconds, stats.collisionDetection)
        assertEquals(3.milliseconds, stats.total)
        assertEquals(2, stats.fixedStepCount)
        assertEquals(10, stats.spriteCount)
        assertEquals(3, stats.drawCallCount)
        assertEquals(4, stats.collisionPairCount)
        assertEquals(800.nanoseconds, stats.particleAdvance)
        assertEquals(5, stats.particleCount)
    }

    @Test
    fun eachFrameStartsFromZero() {
        val stats = FrameStats()
        stats.add(Phase.UPDATE.ordinal, 1_000)
        stats.add(Phase.UPDATE.ordinal, 1_000)
        stats.publish(totalNanos = 0, fixedStepCount = 0, spriteCount = 0, drawCallCount = 0, collisionPairCount = 0, particleCount = 0)
        assertEquals(2_000.nanoseconds, stats.durationOf(Phase.UPDATE))
        stats.publish(totalNanos = 0, fixedStepCount = 0, spriteCount = 0, drawCallCount = 0, collisionPairCount = 0, particleCount = 0)
        assertEquals(Duration.ZERO, stats.durationOf(Phase.UPDATE))
    }

    @Test
    fun startupDurationOutlivesFrames() {
        val stats = FrameStats()
        stats.recordStartup(7_000_000)
        stats.publish(totalNanos = 0, fixedStepCount = 0, spriteCount = 0, drawCallCount = 0, collisionPairCount = 0, particleCount = 0)
        assertEquals(7.milliseconds, stats.durationOf(Phase.STARTUP))
        assertEquals(Duration.ZERO, stats.durationOf(Phase.SHUTDOWN))
    }
}
