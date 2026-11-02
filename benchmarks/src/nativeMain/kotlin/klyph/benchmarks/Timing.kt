package klyph.benchmarks

import klyph.app.FrameStats
import klyph.app.Phase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Turns the [FrameStats] of each frame into one line per second of averages, prefixed by
 * [label], and keeps a run total that skips a warm-up.
 *
 * The warm-up ends after [WARM_UP] or after a quarter of [frameLimit] frames, whichever comes
 * first, so a short run still leaves at least three times as many frames as it skipped. Without a
 * frame limit it lasts [WARM_UP]. The summary line names how long it lasted.
 *
 * The stages are [STAGES]: the fixed update phase (all of the frame's steps, including the
 * engine's copy of interpolated transforms), collision detection, the update phase, transform
 * propagation (both passes), target acquisition, sprite extraction, command recording and
 * submission, the GPU wait, each of [extraStages] (a name and how to read that stage from the
 * stats; they print before "other" and are taken out of it), and "other", the rest of the frame
 * (input, late update, user render systems and the loop itself). The summary also prints how
 * many fixed steps ran per frame on average and, when there were any, how many collision pairs;
 * each line once a second adds the pairs per frame when there were any.
 */
internal class StatsReport(
    frameLimit: Int?,
    private val extraStages: List<Pair<String, (FrameStats) -> Duration>> = emptyList(),
    private val label: () -> String,
) {
    private val clock = TimeSource.Monotonic
    private val stageNames = STAGES.dropLast(1) + extraStages.map { it.first } + STAGES.last()
    private val oneSecond = StageTimes(stageNames)
    private val total = StageTimes(stageNames)

    /** Frames the warm-up may last at most: a quarter of [frameLimit], or no bound without one. */
    private val warmUpFrameLimit = if (frameLimit == null) Int.MAX_VALUE else frameLimit / 4
    private var warmedUp = warmUpFrameLimit == 0
    /** When the first frame was recorded; the warm-up counts from there, not from startup. */
    private var warmUpStart: TimeMark? = null
    private var warmUpFrames = 0
    private var warmUpLength = Duration.ZERO
    private var lastReport = clock.markNow()
    private var oneSecondSteps = 0L
    private var totalSteps = 0L
    private var oneSecondPairs = 0L
    private var totalPairs = 0L

    /**
     * Adds one frame's [stats] to the line of the current second and, once the warm-up is over, to
     * the run total, and prints the line once a second has passed since the last one.
     */
    fun record(stats: FrameStats) {
        add(oneSecond, stats)
        oneSecondSteps += stats.fixedSteps
        oneSecondPairs += stats.collisionPairs
        if (warmedUp) {
            add(total, stats)
            totalSteps += stats.fixedSteps
            totalPairs += stats.collisionPairs
        } else {
            warmUpFrames++
            val start = warmUpStart ?: clock.markNow().also { warmUpStart = it }
            val elapsed = start.elapsedNow()
            if (elapsed >= WARM_UP || warmUpFrames >= warmUpFrameLimit) {
                warmedUp = true
                warmUpLength = elapsed
            }
        }
        if (lastReport.elapsedNow() >= 1.seconds) {
            val pairs = if (oneSecondPairs > 0) " pairs=${oneSecondPairs / oneSecond.frames}" else ""
            println("${label()} ${oneSecond.describe()}$pairs")
            oneSecond.reset()
            oneSecondSteps = 0L
            oneSecondPairs = 0L
            lastReport = clock.markNow()
        }
    }

    /** Adds the stage durations of one frame's [stats] to [times]. */
    private fun add(times: StageTimes, stats: FrameStats) {
        val fixed = stats.durationOf(Phase.FIXED_UPDATE)
        val collide = stats.collisionDetection
        val update = stats.durationOf(Phase.UPDATE)
        val propagate = stats.transformPropagation
        val acquire = stats.targetAcquisition
        val extract = stats.spriteExtraction
        val record = stats.spriteRecording
        val gpuWait = stats.gpuWait
        val frame = stats.total
        times.add(0, fixed)
        times.add(1, collide)
        times.add(2, update)
        times.add(3, propagate)
        times.add(4, acquire)
        times.add(5, extract)
        times.add(6, record)
        times.add(7, gpuWait)
        var other = frame - fixed - collide - update - propagate - acquire - extract - record - gpuWait
        for (k in extraStages.indices) {
            val duration = extraStages[k].second(stats)
            times.add(8 + k, duration)
            other -= duration
        }
        times.add(stageNames.size - 1, other)
        times.endFrame(frame)
    }

    /** Prints the averages over the whole run after the warm-up. */
    fun printSummary() {
        val warmUp = "$warmUpFrames frames (${fixed(warmUpLength.inWholeMicroseconds / 1e6, 2)} s)"
        println("--- summary of ${total.frames} frames after a warm-up of $warmUp ---")
        if (total.frames == 0) {
            println("too short to summarize")
            return
        }
        println("${label()} ${total.describe()}")
        println("fixed steps per frame: ${fixed(totalSteps.toDouble() / total.frames, 3)}")
        if (totalPairs > 0) println("collision pairs per frame: ${fixed(totalPairs.toDouble() / total.frames, 1)}")
    }

    override fun toString(): String = "StatsReport(frames=${total.frames})"

    private companion object {
        /** The longest warm-up, used whole when there is no frame limit or it is far away. */
        val WARM_UP: Duration = 1.seconds

        val STAGES: List<String> = listOf("fixed", "collide", "update", "propagate", "acquire", "extract", "record", "gpuwait", "other")
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
