package klyph.app

import klyph.platform.Color
import klyph.render.Canvas
import klyph.render.DrawSpace
import klyph.text.Font
import klyph.text.forEachLine
import klyph.text.lineWidth
import kotlin.math.abs
import kotlin.math.floor
import kotlin.time.Duration

/**
 * Shows the engine's frame statistics in the window's top-left corner: frame rate, frame and
 * phase times, entity, sprite, draw call, voice and fixed step counts, and a graph of the last 60
 * frame times. `App.overlay` is the one instance.
 *
 * It starts hidden. [AppConfig.overlayKey], F3 unless the game changes it, shows and hides it, and
 * a game can set [isVisible] itself, for example from a debug menu or when it starts with
 * `AppConfig(overlayKey = null)`.
 *
 * While visible, the engine draws it on `App.canvas` after the last [Phase.RENDER] system, in
 * [DrawSpace.SCREEN] at layer [Int.MAX_VALUE] with the built-in font at scale 2, so it covers the
 * game's own screen-space drawing. Its quads count in [FrameStats.spriteCount] and its time in
 * the duration of [Phase.RENDER]. Every number describes the previous frame, as [FrameStats]
 * does; the frame rate and the graph cover the last 60 frames, which the overlay records also
 * while it is hidden. Drawing it allocates nothing once its buffers have grown.
 *
 * Not thread-safe; use it from systems only. Has no sample because it needs a running app.
 */
public class DebugOverlay internal constructor() {
    /**
     * Whether the engine draws the overlay at the end of each frame's [Phase.RENDER]. Starts as
     * `false`; [AppConfig.overlayKey] flips it.
     */
    public var isVisible: Boolean = false

    /** Total durations of the last [FRAME_COUNT] frames. */
    internal val frameTimes: FrameTimes = FrameTimes(FRAME_COUNT)

    /** The left column: frame rate and timings, refilled by [writeText]. */
    internal val timings: StringBuilder = StringBuilder(TIMINGS_CAPACITY)

    /** The right column: counts, refilled by [writeText]. */
    internal val counts: StringBuilder = StringBuilder(COUNTS_CAPACITY)

    /** Adds the total duration of a frame that just ended to the last [FRAME_COUNT] frames. */
    internal fun record(total: Duration) {
        frameTimes.add(total.inWholeNanoseconds)
    }

    /**
     * Fills [timings] and [counts] with the lines the overlay shows for [stats] and [voices]
     * playing sound voices. Labels are padded to one width and numbers
     * right-aligned, so the numbers line up in the monospace built-in font.
     */
    internal fun writeText(stats: FrameStats, voices: Int) {
        val meanMillis = frameTimes.meanNanos() / NANOS_PER_MILLI
        val fps = if (meanMillis > 0.0) 1000.0 / meanMillis else 0.0
        timings.setLength(0)
        timings.appendLabel("fps")
        timings.appendFixed(fps, decimals = 1, width = VALUE_WIDTH)
        timings.append('\n')
        timings.appendMillis("frame", meanMillis)
        timings.appendMillis("frame max", frameTimes.maxNanos() / NANOS_PER_MILLI)
        timings.appendDuration("fixed", stats.durationOf(Phase.FIXED_UPDATE))
        timings.appendDuration("update", stats.durationOf(Phase.UPDATE))
        timings.appendDuration("late", stats.durationOf(Phase.LATE_UPDATE))
        timings.appendDuration("render", stats.durationOf(Phase.RENDER))
        timings.appendDuration("propagate", stats.transformPropagation)
        timings.appendDuration("extract", stats.spriteExtraction)
        timings.appendDuration("record", stats.spriteRecording)
        timings.appendDuration("gpu wait", stats.gpuWait)
        timings.setLength(timings.length - 1)

        counts.setLength(0)
        counts.appendCount("entities", stats.entityCount)
        counts.appendCount("sprites", stats.spriteCount)
        counts.appendCount("draws", stats.drawCalls)
        counts.appendCount("voices", voices)
        counts.appendCount("steps", stats.fixedSteps)
        counts.setLength(counts.length - 1)
    }

    /**
     * Queues the overlay on [canvas], which must be open, with [font] at scale 2: a translucent
     * panel, the two text columns [writeText] fills, one bar per recorded frame and a line at
     * [fixedDelta] across the graph. Returns the number of quads queued.
     */
    internal fun draw(canvas: Canvas, font: Font, stats: FrameStats, fixedDelta: Duration, voices: Int): Int {
        val before = canvas.count
        writeText(stats, voices)
        val lineHeight = font.lineHeight * TEXT_SCALE
        val left = MARGIN + PADDING
        val top = MARGIN + PADDING
        val timingsWidth = font.widestLine(timings) * TEXT_SCALE
        val countsWidth = font.widestLine(counts) * TEXT_SCALE
        val rows = maxOf(timings.lineCount(), counts.lineCount())
        val graphTop = top + rows * lineHeight + GRAPH_GAP
        val graphBottom = graphTop + GRAPH_HEIGHT
        val graphWidth = FRAME_COUNT * BAR_WIDTH
        val panelWidth = 2f * PADDING + maxOf(timingsWidth + COLUMN_GAP + countsWidth, graphWidth)
        val panelHeight = graphBottom + PADDING - MARGIN

        canvas.drawRect(x = MARGIN, y = MARGIN, width = panelWidth, height = panelHeight, color = PANEL_COLOR, layer = LAYER, space = DrawSpace.SCREEN)
        canvas.drawText(font, timings, x = left, y = top, scale = TEXT_SCALE, layer = LAYER, space = DrawSpace.SCREEN)
        canvas.drawText(font, counts, x = left + timingsWidth + COLUMN_GAP, y = top, scale = TEXT_SCALE, layer = LAYER, space = DrawSpace.SCREEN)

        val fixedNanos = fixedDelta.inWholeNanoseconds
        val frames = frameTimes.count
        val firstX = left + (FRAME_COUNT - frames) * BAR_WIDTH
        for (i in 0 until frames) {
            val nanos = frameTimes[i]
            val height = barHeight(nanos)
            val color = if (nanos > fixedNanos) SLOW_BAR_COLOR else BAR_COLOR
            canvas.drawRect(
                x = firstX + i * BAR_WIDTH, y = graphBottom - height, width = BAR_WIDTH, height = height,
                color = color, layer = LAYER, space = DrawSpace.SCREEN,
            )
        }
        val lineY = graphBottom - barHeight(fixedNanos)
        canvas.drawRect(x = left, y = lineY, width = graphWidth, height = 1f, color = LINE_COLOR, layer = LAYER, space = DrawSpace.SCREEN)
        return canvas.count - before
    }

    /** Returns `DebugOverlay(isVisible=false)`. The format may change. */
    override fun toString(): String = "DebugOverlay(isVisible=$isVisible)"

    internal companion object {
        /** Number of frames the frame rate and the graph cover. */
        const val FRAME_COUNT: Int = 60

        /** The layer every quad of the overlay is drawn in, above anything a game draws. */
        const val LAYER: Int = Int.MAX_VALUE

        /** Text scale: the built-in font's 8 point glyphs drawn 16 points tall. */
        const val TEXT_SCALE: Float = 2f

        /** Distance from the window's top-left corner to the panel, in points. */
        const val MARGIN: Float = 8f

        /** Space between the panel's edge and its contents, in points. */
        const val PADDING: Float = 8f

        /** Space between the two text columns, in points. */
        const val COLUMN_GAP: Float = 16f

        /** Space between the text and the graph, in points. */
        const val GRAPH_GAP: Float = 8f

        /** Width of one frame's bar, in points. */
        const val BAR_WIDTH: Float = 3f

        /** Height of the tallest bar, in points. */
        const val GRAPH_HEIGHT: Float = 64f

        /** Bar height per millisecond of frame time: 16.7 ms, one 60 Hz frame, is 32 points. */
        const val POINTS_PER_MILLI: Float = 32f / 16.7f

        /** Width of every label, in characters. */
        const val LABEL_WIDTH: Int = 10

        /** Width of every number, in characters. */
        const val VALUE_WIDTH: Int = 7

        private const val NANOS_PER_MILLI = 1_000_000.0
        private const val TIMINGS_CAPACITY = 256
        private const val COUNTS_CAPACITY = 128

        val PANEL_COLOR: Color = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.7f)
        val BAR_COLOR: Color = Color(red = 0.45f, green = 0.85f, blue = 0.45f)
        val SLOW_BAR_COLOR: Color = Color(red = 1f, green = 0.3f, blue = 0.25f)
        val LINE_COLOR: Color = Color(red = 1f, green = 0.85f, blue = 0.3f)

        /** Returns the height in points of the bar for a frame of [nanos], between 1 and [GRAPH_HEIGHT]. */
        fun barHeight(nanos: Long): Float =
            ((nanos / NANOS_PER_MILLI).toFloat() * POINTS_PER_MILLI).coerceIn(1f, GRAPH_HEIGHT)

        private fun StringBuilder.appendLabel(label: String) {
            append(label)
            repeat(LABEL_WIDTH - label.length) { append(' ') }
        }

        private fun StringBuilder.appendMillis(label: String, millis: Double) {
            appendLabel(label)
            appendFixed(millis, decimals = 2, width = VALUE_WIDTH)
            append(" ms\n")
        }

        private fun StringBuilder.appendDuration(label: String, duration: Duration) {
            appendMillis(label, duration.inWholeNanoseconds / NANOS_PER_MILLI)
        }

        private fun StringBuilder.appendCount(label: String, count: Int) {
            appendLabel(label)
            appendFixed(count.toDouble(), decimals = 0, width = VALUE_WIDTH)
            append('\n')
        }

        private fun CharSequence.lineCount(): Int {
            var lines = 1
            for (i in indices) if (this[i] == '\n') lines++
            return lines
        }

        private fun Font.widestLine(text: CharSequence): Float {
            var widest = 0f
            forEachLine(text) { start, end ->
                val width = lineWidth(text, start, end)
                if (width > widest) widest = width
            }
            return widest
        }
    }
}

/**
 * Holds the last [capacity] frame durations in nanoseconds, oldest first, overwriting the oldest
 * once full. Allocates nothing after construction.
 */
internal class FrameTimes(val capacity: Int) {
    private val nanos = LongArray(capacity)
    private var next = 0

    init {
        require(capacity > 0) { "Frame time capacity $capacity is not positive." }
    }

    /** Number of durations held, at most [capacity]. */
    var count: Int = 0
        private set

    /** Adds a duration of [nanos], dropping the oldest when [count] has reached [capacity]. */
    fun add(nanos: Long) {
        this.nanos[next] = nanos
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** Returns the duration at [index], 0 being the oldest held. */
    operator fun get(index: Int): Long {
        if (index !in 0 until count) throw IndexOutOfBoundsException("Index $index is out of bounds for size $count.")
        val start = if (count < capacity) 0 else next
        return nanos[(start + index) % capacity]
    }

    /** Returns the mean of the durations held, or 0 when there are none. */
    fun meanNanos(): Double {
        if (count == 0) return 0.0
        var sum = 0L
        for (i in 0 until count) sum += nanos[i]
        return sum.toDouble() / count
    }

    /** Returns the longest duration held, or 0 when there are none. */
    fun maxNanos(): Long {
        var max = 0L
        for (i in 0 until count) if (nanos[i] > max) max = nanos[i]
        return max
    }

    override fun toString(): String = "FrameTimes(count=$count, capacity=$capacity)"
}

/**
 * Appends [value] rounded half up to [decimals] digits after the point, with a leading `-` when the
 * rounded value is negative, right-aligned with spaces to at least [width] characters, and
 * returns this builder. [decimals] must be between 0 and 9. Not a number appends `NaN`, and a
 * value too large for 18 digits appends `inf` or `-inf`. Allocates nothing beyond the builder's
 * own growth, unlike `String.format` and `Double.toString`.
 */
internal fun StringBuilder.appendFixed(value: Double, decimals: Int, width: Int = 0): StringBuilder {
    require(decimals in 0..MAX_DECIMALS) { "Decimal count $decimals is not between 0 and $MAX_DECIMALS." }
    if (value.isNaN()) return appendPadded("NaN", width)
    var unit = 1L
    repeat(decimals) { unit *= 10 }
    val scaled = floor(abs(value) * unit + 0.5)
    if (scaled >= MAX_SCALED) return appendPadded(if (value < 0) "-inf" else "inf", width)
    val units = scaled.toLong()
    val negative = value < 0 && units != 0L
    val integer = units / unit
    val fraction = units % unit
    val integerDigits = digitCount(integer)
    val length = (if (negative) 1 else 0) + integerDigits + (if (decimals > 0) 1 + decimals else 0)
    repeat(width - length) { append(' ') }
    if (negative) append('-')
    appendDigits(integer, integerDigits)
    if (decimals > 0) {
        append('.')
        appendDigits(fraction, decimals)
    }
    return this
}

private const val MAX_DECIMALS = 9

/** Smallest scaled value [appendFixed] prints as infinite: 10^18, past which a `Long` runs short. */
private const val MAX_SCALED = 1e18

private fun StringBuilder.appendPadded(text: String, width: Int): StringBuilder {
    repeat(width - text.length) { append(' ') }
    return append(text)
}

/** Returns the number of decimal digits of [value], which is not negative; 1 for 0. */
private fun digitCount(value: Long): Int {
    var digits = 1
    var rest = value / 10
    while (rest > 0) {
        digits++
        rest /= 10
    }
    return digits
}

/** Appends the last [count] decimal digits of [value], which is not negative, with leading zeros. */
private fun StringBuilder.appendDigits(value: Long, count: Int) {
    var divisor = 1L
    repeat(count - 1) { divisor *= 10 }
    while (divisor > 0) {
        append('0' + ((value / divisor) % 10).toInt())
        divisor /= 10
    }
}
