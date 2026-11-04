package klyph.app

import klyph.platform.Color
import klyph.render.Canvas
import klyph.render.DrawSpace
import klyph.render.SPRITE_RECORD_FLOATS
import klyph.render.Texture
import klyph.text.BuiltInFont
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class DebugOverlayTest {
    private val builtIn = BuiltInFont.over(Texture(BuiltInFont.ATLAS_WIDTH, BuiltInFont.ATLAS_HEIGHT))
    private val font = builtIn.font

    /** Returns stats as if the loop had published one frame with these timings and counts. */
    private fun publishedStats(): FrameStats {
        val stats = FrameStats()
        stats.add(Phase.FIXED_UPDATE.ordinal, 1_250_000)
        stats.add(Phase.UPDATE.ordinal, 300_000)
        stats.add(Phase.LATE_UPDATE.ordinal, 6_000)
        stats.add(Phase.RENDER.ordinal, 2_004_999)
        stats.add(FrameStats.PROPAGATION, 50_000)
        stats.add(FrameStats.EXTRACTION, 400_000)
        stats.add(FrameStats.RECORDING, 123_456)
        stats.entityCount = 1234
        stats.publish(totalNanos = 16_000_000, fixedStepCount = 2, spriteCount = 100_000, drawCallCount = 3, collisionPairCount = 0, particleCount = 0)
        return stats
    }

    @Test
    fun writesAlignedLinesFromStats() {
        val overlay = DebugOverlay()
        overlay.record(10.milliseconds)
        overlay.record(30.milliseconds)
        overlay.writeText(publishedStats(), voices = 5)
        assertEquals(
            """
            fps          50.0
            frame       20.00 ms
            frame max   30.00 ms
            fixed        1.25 ms
            update       0.30 ms
            late         0.01 ms
            render       2.00 ms
            propagate    0.05 ms
            extract      0.40 ms
            record       0.12 ms
            gpu wait     0.00 ms
            """.trimIndent(),
            overlay.timings.toString(),
        )
        assertEquals(
            """
            entities     1234
            sprites    100000
            draws           3
            voices          5
            steps           2
            """.trimIndent(),
            overlay.counts.toString(),
        )
    }

    @Test
    fun showsZeroFramesPerSecondBeforeTheFirstFrame() {
        val overlay = DebugOverlay()
        overlay.writeText(FrameStats(), voices = 0)
        assertTrue(overlay.timings.startsWith("fps           0.0\nframe        0.00 ms\n"), overlay.timings.toString())
    }

    @Test
    fun drawQueuesPanelTextBarsAndLine() {
        val overlay = DebugOverlay()
        repeat(70) { overlay.record(if (it % 2 == 0) 10.milliseconds else 20.milliseconds) }
        val canvas = Canvas(builtIn.whitePixel)
        canvas.open()
        val queued = overlay.draw(canvas, font, publishedStats(), fixedDelta = 16.milliseconds, voices = 5)

        // The space glyph draws nothing, so every other character of both columns is one quad.
        val glyphs = (overlay.timings.toString() + overlay.counts).count { it != ' ' && it != '\n' }
        assertEquals(1 + glyphs + DebugOverlay.FRAME_COUNT + 1, queued)
        assertEquals(queued, canvas.quadCount)
        for (i in 0 until canvas.quadCount) {
            assertEquals(DebugOverlay.LAYER, canvas.quads.layers[i])
            assertEquals(DrawSpace.SCREEN, canvas.quads.spaces[i])
        }
        // The panel comes first, translucent, its top-left corner 8 points from the window's.
        assertEquals(channelsOf(DebugOverlay.PANEL_COLOR), colorOf(canvas, 0))
        assertTrue(DebugOverlay.PANEL_COLOR.alpha < 1f)
        val panelLeft = recordOf(canvas, 0, 0) - 0.5f * recordOf(canvas, 0, 4)
        val panelTop = recordOf(canvas, 0, 1) - 0.5f * recordOf(canvas, 0, 5)
        assertEquals(8f, panelLeft)
        assertEquals(8f, panelTop)
        // Bars alternate: 10 ms is under the 16 ms step, 20 ms over it and red. The newest is last.
        val firstBar = 1 + glyphs
        val lastBar = firstBar + DebugOverlay.FRAME_COUNT - 1
        assertEquals(channelsOf(DebugOverlay.BAR_COLOR), colorOf(canvas, firstBar))
        assertEquals(channelsOf(DebugOverlay.SLOW_BAR_COLOR), colorOf(canvas, lastBar))
        assertEquals(DebugOverlay.barHeight(20_000_000), recordOf(canvas, lastBar, 5))
        assertEquals(3f, recordOf(canvas, lastBar, 4))
        // The line at the fixed step spans the graph, one point high.
        val line = canvas.quadCount - 1
        assertEquals(channelsOf(DebugOverlay.LINE_COLOR), colorOf(canvas, line))
        assertEquals(180f, recordOf(canvas, line, 4))
        assertEquals(1f, recordOf(canvas, line, 5))
    }

    @Test
    fun drawShowsOnlyRecordedFramesAlignedRight() {
        val overlay = DebugOverlay()
        overlay.record(5.milliseconds)
        overlay.record(6.milliseconds)
        val canvas = Canvas(builtIn.whitePixel)
        canvas.open()
        val queued = overlay.draw(canvas, font, FrameStats(), fixedDelta = 16.milliseconds, voices = 0)
        val glyphs = (overlay.timings.toString() + overlay.counts).count { it != ' ' && it != '\n' }
        assertEquals(1 + glyphs + 2 + 1, queued)
        // The newest bar ends where the line does: at the graph's right edge.
        val newest = 1 + glyphs + 1
        val line = queued - 1
        val newestRight = recordOf(canvas, newest, 0) + 0.5f * recordOf(canvas, newest, 4)
        val lineRight = recordOf(canvas, line, 0) + 0.5f * recordOf(canvas, line, 4)
        assertEquals(lineRight, newestRight)
    }

    @Test
    fun drawNeedsAnOpenList() {
        val canvas = Canvas(builtIn.whitePixel)
        assertFailsWith<IllegalStateException> { DebugOverlay().draw(canvas, font, FrameStats(), 16.milliseconds, voices = 0) }
    }

    @Test
    fun barHeightScalesAndClamps() {
        assertTrue(abs(DebugOverlay.barHeight(16_700_000) - 32f) < 1e-3f)
        assertEquals(64f, DebugOverlay.barHeight(100_000_000))
        assertEquals(1f, DebugOverlay.barHeight(0))
    }

    @Test
    fun visibilityToggles() {
        val overlay = DebugOverlay()
        assertFalse(overlay.isVisible)
        assertEquals("DebugOverlay(isVisible=false)", overlay.toString())
        overlay.isVisible = !overlay.isVisible
        assertTrue(overlay.isVisible)
        assertEquals("DebugOverlay(isVisible=true)", overlay.toString())
        overlay.isVisible = !overlay.isVisible
        assertFalse(overlay.isVisible)
    }

    @Test
    fun frameTimesMeanAndMaxCoverTheLastFrames() {
        val times = FrameTimes(3)
        assertEquals(0.0, times.meanNanos())
        assertEquals(0L, times.maxNanos())
        times.add(10)
        times.add(40)
        assertEquals(2, times.count)
        assertEquals(25.0, times.meanNanos())
        assertEquals(40L, times.maxNanos())
        assertEquals(10L, times[0])
        assertEquals(40L, times[1])

        times.add(20)
        times.add(30)
        // 10 is overwritten; the oldest held is now 40.
        assertEquals(3, times.count)
        assertEquals(30.0, times.meanNanos())
        assertEquals(40L, times.maxNanos())
        assertEquals(listOf(40L, 20L, 30L), List(times.count) { times[it] })
        times.add(5)
        times.add(5)
        assertEquals(30L, times.maxNanos())
        assertFailsWith<IndexOutOfBoundsException> { times[3] }
        assertFailsWith<IllegalArgumentException> { FrameTimes(0) }
    }

    @Test
    fun overlayRecordsFrameTotals() {
        val overlay = DebugOverlay()
        repeat(DebugOverlay.FRAME_COUNT + 5) { overlay.record((it + 1).milliseconds) }
        assertEquals(DebugOverlay.FRAME_COUNT, overlay.frameTimes.count)
        assertEquals(6_000_000L, overlay.frameTimes[0])
        assertEquals(65_000_000L, overlay.frameTimes.maxNanos())
        overlay.record(1.nanoseconds)
        assertEquals(1L, overlay.frameTimes[DebugOverlay.FRAME_COUNT - 1])
    }

    @Test
    fun appendFixedRoundsAndPads() {
        fun format(value: Double, decimals: Int, width: Int = 0) = StringBuilder().appendFixed(value, decimals, width).toString()
        assertEquals("16.67", format(16.666, 2))
        assertEquals("  16.67", format(16.666, 2, width = 7))
        assertEquals("0.00", format(0.0, 2))
        assertEquals("0.05", format(0.05, 2))
        assertEquals("1.0", format(0.96, 1))
        assertEquals("1234", format(1234.4, 0))
        assertEquals("   1235", format(1234.5, 0, width = 7))
        assertEquals("-1.50", format(-1.5, 2))
        assertEquals("0.00", format(-0.001, 2))
        assertEquals("123456789", format(123456789.0, 0, width = 3))
        assertEquals("0.000000001", format(1e-9, 9))
        assertEquals("  NaN", format(Double.NaN, 2, width = 5))
        assertEquals("inf", format(Double.POSITIVE_INFINITY, 2))
        assertEquals("-inf", format(-1e30, 2))
        assertEquals("ab12.0", StringBuilder("ab").appendFixed(12.0, 1).toString())
        assertFailsWith<IllegalArgumentException> { format(1.0, 10) }
        assertFailsWith<IllegalArgumentException> { format(1.0, -1) }
    }

    private fun recordOf(canvas: Canvas, quad: Int, float: Int): Float = canvas.quads.records[quad * SPRITE_RECORD_FLOATS + float]

    /** Returns the tint of [quad] as red, green, blue and alpha. */
    private fun colorOf(canvas: Canvas, quad: Int): List<Float> = (12..15).map { recordOf(canvas, quad, it) }

    private fun channelsOf(color: Color): List<Float> = listOf(color.red, color.green, color.blue, color.alpha)
}
