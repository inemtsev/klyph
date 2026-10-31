package klyph.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class AnimationTest {
    private fun Animation.framesOver(steps: Int, delta: Duration): List<Int> =
        List(steps) { advance(delta); frameIndex }

    @Test
    fun startsOnFrameZero() {
        val animation = Animation(frameCount = 4, frameDuration = 100.milliseconds)
        assertEquals(0, animation.frameIndex)
        assertEquals(Duration.ZERO, animation.elapsed)
        assertFalse(animation.isFinished)
        assertFalse(animation.isPaused)
        assertEquals(AnimationMode.LOOP, animation.mode)
    }

    @Test
    fun loopWrapsAndKeepsRemainder() {
        val animation = Animation(frameCount = 3, frameDuration = 100.milliseconds)
        assertEquals(listOf(1, 2, 0, 1), animation.framesOver(4, 100.milliseconds))
        animation.advance(250.milliseconds)
        assertEquals(0, animation.frameIndex)
        assertEquals(50.milliseconds, animation.elapsed)
        animation.advance(50.milliseconds)
        assertEquals(1, animation.frameIndex)
        assertEquals(Duration.ZERO, animation.elapsed)
        assertFalse(animation.isFinished)
    }

    @Test
    fun deltaLongerThanWholeLoopWraps() {
        val animation = Animation(frameCount = 4, frameDuration = 100.milliseconds)
        animation.advance(1.seconds + 230.milliseconds)
        // 12 whole frames is three loops; 30 ms carry over.
        assertEquals(0, animation.frameIndex)
        assertEquals(30.milliseconds, animation.elapsed)
    }

    @Test
    fun onceFinishesOnLastFrameAndStays() {
        val animation = Animation(frameCount = 3, frameDuration = 100.milliseconds, mode = AnimationMode.ONCE)
        animation.advance(250.milliseconds)
        assertEquals(2, animation.frameIndex)
        assertFalse(animation.isFinished)
        animation.advance(49.milliseconds)
        assertFalse(animation.isFinished)
        animation.advance(1.milliseconds)
        assertTrue(animation.isFinished)
        assertEquals(2, animation.frameIndex)
        assertEquals(100.milliseconds, animation.elapsed)
        animation.advance(10.seconds)
        assertEquals(2, animation.frameIndex)
        assertEquals(100.milliseconds, animation.elapsed)
        assertTrue(animation.isFinished)
    }

    @Test
    fun onceWithDeltaLongerThanAnimationFinishes() {
        val animation = Animation(frameCount = 3, frameDuration = 100.milliseconds, mode = AnimationMode.ONCE)
        animation.advance(Duration.INFINITE)
        assertTrue(animation.isFinished)
        assertEquals(2, animation.frameIndex)
    }

    @Test
    fun pingPongRunsForwardThenBackward() {
        val animation = Animation(frameCount = 4, frameDuration = 100.milliseconds, mode = AnimationMode.PING_PONG)
        assertEquals(
            listOf(1, 2, 3, 2, 1, 0, 1, 2, 3, 2),
            animation.framesOver(10, 100.milliseconds),
        )
        assertFalse(animation.isFinished)
    }

    @Test
    fun pingPongWithOneFrameNeverChanges() {
        val animation = Animation(frameCount = 1, frameDuration = 100.milliseconds, mode = AnimationMode.PING_PONG)
        assertEquals(List(5) { 0 }, animation.framesOver(5, 100.milliseconds))
        animation.advance(Duration.INFINITE)
        assertEquals(0, animation.frameIndex)
    }

    @Test
    fun pingPongWithTwoFramesAlternates() {
        val animation = Animation(frameCount = 2, frameDuration = 100.milliseconds, mode = AnimationMode.PING_PONG)
        assertEquals(listOf(1, 0, 1, 0, 1), animation.framesOver(5, 100.milliseconds))
    }

    @Test
    fun deltaOfTwoAndAHalfFramesAdvancesTwoAndKeepsHalf() {
        val animation = Animation(frameCount = 8, frameDuration = 100.milliseconds)
        animation.advance(250.milliseconds)
        assertEquals(2, animation.frameIndex)
        assertEquals(50.milliseconds, animation.elapsed)
        animation.advance(50.milliseconds)
        assertEquals(3, animation.frameIndex)
    }

    @Test
    fun pausedIgnoresAdvance() {
        val animation = Animation(frameCount = 4, frameDuration = 100.milliseconds)
        animation.isPaused = true
        animation.advance(350.milliseconds)
        assertEquals(0, animation.frameIndex)
        assertEquals(Duration.ZERO, animation.elapsed)
        animation.isPaused = false
        animation.advance(150.milliseconds)
        assertEquals(1, animation.frameIndex)
    }

    @Test
    fun restartClearsEverything() {
        val animation = Animation(frameCount = 3, frameDuration = 100.milliseconds, mode = AnimationMode.ONCE)
        animation.advance(1.seconds)
        animation.isPaused = true
        animation.restart()
        assertEquals(0, animation.frameIndex)
        assertEquals(Duration.ZERO, animation.elapsed)
        assertFalse(animation.isFinished)
        assertFalse(animation.isPaused)
        animation.advance(100.milliseconds)
        assertEquals(1, animation.frameIndex)
    }

    @Test
    fun seekJumpsAndClearsFinished() {
        val animation = Animation(frameCount = 4, frameDuration = 100.milliseconds, mode = AnimationMode.ONCE)
        animation.advance(1.seconds)
        assertTrue(animation.isFinished)
        animation.isPaused = true
        animation.seek(1)
        assertEquals(1, animation.frameIndex)
        assertEquals(Duration.ZERO, animation.elapsed)
        assertFalse(animation.isFinished)
        assertTrue(animation.isPaused)
    }

    @Test
    fun seekInPingPongPlaysForward() {
        val animation = Animation(frameCount = 4, frameDuration = 100.milliseconds, mode = AnimationMode.PING_PONG)
        animation.advance(400.milliseconds)
        assertEquals(2, animation.frameIndex) // on the way back
        animation.seek(2)
        assertEquals(listOf(3, 2), animation.framesOver(2, 100.milliseconds))
    }

    @Test
    fun seekRejectsOutOfRange() {
        val animation = Animation(frameCount = 4, frameDuration = 100.milliseconds)
        assertFailsWith<IndexOutOfBoundsException> { animation.seek(-1) }
        assertFailsWith<IndexOutOfBoundsException> { animation.seek(4) }
        animation.seek(3)
        assertEquals(3, animation.frameIndex)
    }

    @Test
    fun rejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { Animation(0, 100.milliseconds) }
        assertFailsWith<IllegalArgumentException> { Animation(4, Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { Animation(4, (-1).milliseconds) }
        assertFailsWith<IllegalArgumentException> { Animation(4, Duration.INFINITE) }
        val animation = Animation(4, 100.milliseconds)
        assertFailsWith<IllegalArgumentException> { animation.advance((-1).nanoseconds) }
        animation.isPaused = true
        assertFailsWith<IllegalArgumentException> { animation.advance((-1).nanoseconds) }
    }

    @Test
    fun sixtyHertzStepsThroughHundredMillisecondFramesWithoutDrift() {
        val frameCount = 7
        val animation = Animation(frameCount = frameCount, frameDuration = 100.milliseconds)
        // A 60 Hz clock in whole nanoseconds: step i covers [i/60 s, (i+1)/60 s), so every 60 steps
        // sum to exactly one second even though one step is not a whole number of nanoseconds.
        fun stepDelta(i: Long): Duration = ((i + 1) * 1_000_000_000L / 60 - i * 1_000_000_000L / 60).nanoseconds
        for (i in 0L..<600L) {
            animation.advance(stepDelta(i))
            assertEquals(((i + 1) / 6 % frameCount).toInt(), animation.frameIndex, "Step ${i + 1}.")
        }
        assertEquals((600 / 6) % frameCount, animation.frameIndex)
        assertEquals(Duration.ZERO, animation.elapsed)
    }

    @Test
    fun durationIsOneCycle() {
        assertEquals(800.milliseconds, Animation(8, 100.milliseconds).duration)
        assertEquals(800.milliseconds, Animation(8, 100.milliseconds, AnimationMode.ONCE).duration)
        assertEquals(1400.milliseconds, Animation(8, 100.milliseconds, AnimationMode.PING_PONG).duration)
        assertEquals(100.milliseconds, Animation(1, 100.milliseconds, AnimationMode.PING_PONG).duration)
        assertEquals(200.milliseconds, Animation(2, 100.milliseconds, AnimationMode.PING_PONG).duration)
    }

    @Test
    fun toStringShowsFrameAndMode() {
        val animation = Animation(frameCount = 8, frameDuration = 100.milliseconds)
        animation.advance(200.milliseconds)
        assertEquals("Animation(frame=2/8, mode=LOOP)", animation.toString())
        assertEquals("ComponentType(Animation)", Animation.toString())
    }
}
