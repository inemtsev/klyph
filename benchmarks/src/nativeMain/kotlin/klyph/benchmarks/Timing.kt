package klyph.benchmarks

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Times each stage of every frame. Prints one line per second of averages, prefixed by [label],
 * and keeps a run total that skips the first second as warm-up.
 *
 * A frame is [beginFrame], one [endStage] per name in [stageNames], then [endFrame]. Each stage
 * lasts from the end of the previous one, so the stages add up to the whole frame.
 */
internal class StageTimer(private val stageNames: List<String>, private val label: () -> String) {
    private val clock = TimeSource.Monotonic
    private val oneSecond = StageTimes(stageNames)
    private val total = StageTimes(stageNames)
    private var warmedUp = false
    private var frameStart = clock.markNow()
    private var stageStart = frameStart
    private var lastReport = frameStart
    private var stage = 0

    /** Starts a frame and returns the time since the previous frame started, or since the timer was created. */
    fun beginFrame(): Duration {
        val now = clock.markNow()
        val sincePrevious = now - frameStart
        frameStart = now
        stageStart = now
        stage = 0
        return sincePrevious
    }

    /** Ends the current stage and starts the next. */
    fun endStage() {
        val now = clock.markNow()
        oneSecond.add(stage, now - stageStart)
        stage++
        stageStart = now
    }

    /** Ends the frame at the end of its last stage, and prints the averages once a second has passed. */
    fun endFrame() {
        check(stage == stageNames.size) { "Frame ended after $stage stages; expected ${stageNames.size}." }
        oneSecond.endFrame(stageStart - frameStart)
        if (stageStart - lastReport >= 1.seconds) {
            println("${label()} ${oneSecond.describe()}")
            if (warmedUp) total.addAll(oneSecond) else warmedUp = true
            oneSecond.reset()
            lastReport = stageStart
        }
    }

    /** Prints the averages over the whole run after the warm-up second. */
    fun printSummary() {
        println("--- summary (after 1 s warm-up, ${total.frames} frames) ---")
        println(if (total.frames == 0) "too short to summarize" else total.describe())
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
