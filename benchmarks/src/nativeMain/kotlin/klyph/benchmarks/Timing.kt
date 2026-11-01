package klyph.benchmarks

import klyph.app.FrameStats
import klyph.app.Phase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Turns the [FrameStats] of each frame into one line per second of averages, prefixed by
 * [label], and keeps a run total that skips the first second as warm-up.
 *
 * The stages are [STAGES]: the fixed update phase (all of the frame's steps, including the
 * engine's copy of interpolated transforms), the update phase, transform propagation, target
 * acquisition, sprite extraction, command recording and submission, the GPU wait, and "other",
 * the rest of the frame (input, late update, user render systems and the loop itself). The
 * summary also prints how many fixed steps ran per frame on average.
 */
internal class StatsReport(private val label: () -> String) {
    private val clock = TimeSource.Monotonic
    private val oneSecond = StageTimes(STAGES)
    private val total = StageTimes(STAGES)
    private var warmedUp = false
    private var lastReport = clock.markNow()
    private var oneSecondSteps = 0L
    private var totalSteps = 0L

    /** Adds one frame's [stats], and prints the averages once a second has passed since the last line. */
    fun record(stats: FrameStats) {
        val fixed = stats.durationOf(Phase.FIXED_UPDATE)
        val update = stats.durationOf(Phase.UPDATE)
        val propagate = stats.transformPropagation
        val acquire = stats.targetAcquisition
        val extract = stats.spriteExtraction
        val record = stats.spriteRecording
        val gpuWait = stats.gpuWait
        val frame = stats.total
        oneSecond.add(0, fixed)
        oneSecond.add(1, update)
        oneSecond.add(2, propagate)
        oneSecond.add(3, acquire)
        oneSecond.add(4, extract)
        oneSecond.add(5, record)
        oneSecond.add(6, gpuWait)
        oneSecond.add(7, frame - fixed - update - propagate - acquire - extract - record - gpuWait)
        oneSecond.endFrame(frame)
        oneSecondSteps += stats.fixedSteps
        if (lastReport.elapsedNow() >= 1.seconds) {
            println("${label()} ${oneSecond.describe()}")
            if (warmedUp) {
                total.addAll(oneSecond)
                totalSteps += oneSecondSteps
            } else {
                warmedUp = true
            }
            oneSecond.reset()
            oneSecondSteps = 0L
            lastReport = clock.markNow()
        }
    }

    /** Prints the averages over the whole run after the warm-up second. */
    fun printSummary() {
        println("--- summary (after 1 s warm-up, ${total.frames} frames) ---")
        if (total.frames == 0) {
            println("too short to summarize")
            return
        }
        println("${label()} ${total.describe()}")
        println("fixed steps per frame: ${fixed(totalSteps.toDouble() / total.frames, 3)}")
    }

    override fun toString(): String = "StatsReport(frames=${total.frames})"

    private companion object {
        val STAGES: List<String> = listOf("fixed", "update", "propagate", "acquire", "extract", "record", "gpuwait", "other")
    }
}

/** Sums of per-stage durations over a number of frames. */
internal class StageTimes(private val stageNames: List<String>) {
    var frames: Int = 0
        private set
    private val stageNanos = LongArray(stageNames.size)
    private var frameNanos = 0L

    fun add(stage: Int, duration: Duration) {
        stageNanos[stage] += duration.inWholeNanoseconds
    }

    fun endFrame(frame: Duration) {
        frames++
        frameNanos += frame.inWholeNanoseconds
    }

    fun addAll(other: StageTimes) {
        frames += other.frames
        frameNanos += other.frameNanos
        for (i in stageNanos.indices) stageNanos[i] += other.stageNanos[i]
    }

    fun reset() {
        frames = 0
        frameNanos = 0L
        stageNanos.fill(0L)
    }

    /** Returns frames per second and the average milliseconds per frame of the frame and each stage. */
    fun describe(): String {
        if (frames == 0) return "no frames"
        fun ms(nanos: Long) = fixed(nanos / 1000 / frames / 1000.0, 3)
        val fps = frames / (frameNanos / 1000 / 1e6)
        return buildString {
            append("fps=").append(fixed(fps, 0)).append(" frame=").append(ms(frameNanos)).append("ms")
            for (i in stageNames.indices) append(' ').append(stageNames[i]).append('=').append(ms(stageNanos[i])).append("ms")
        }
    }
}

/** Formats [value] with [decimals] digits after the point; Kotlin/Native has no String.format. */
internal fun fixed(value: Double, decimals: Int): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = (value * scale + 0.5).toLong()
    val whole = scaled / scale
    if (decimals == 0) return whole.toString()
    val fraction = (scaled % scale).toString().padStart(decimals, '0')
    return "$whole.$fraction"
}
