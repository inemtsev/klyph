package samples.render

import klyph.render.Animation
import klyph.render.AnimationMode
import klyph.render.duration
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class Animations {
    @Sample
    fun advance() {
        val walk = Animation(frameCount = 4, frameDuration = 100.milliseconds)
        walk.advance(250.milliseconds)
        assertPrints(walk.frameIndex, "2")
        assertPrints(walk.frameElapsed, "50ms")
        // LOOP wraps from the last frame back to the first.
        walk.advance(150.milliseconds)
        assertPrints(walk, "Animation(frame=0/4, mode=LOOP, speed=1.0)")
    }

    @Sample
    fun speed() {
        val walk = Animation(frameCount = 4, frameDuration = 100.milliseconds)
        // Twice as fast: 100 milliseconds of game time move the playhead two frames.
        walk.speed = 2f
        walk.advance(100.milliseconds)
        assertPrints(walk.frameIndex, "2")
        // Zero holds the current frame.
        walk.speed = 0f
        walk.advance(1000.milliseconds)
        assertPrints(walk.frameIndex, "2")
        assertFailsWith<IllegalArgumentException> { walk.speed = -1f }
    }

    @Sample
    fun once() {
        val explosion = Animation(frameCount = 3, frameDuration = 100.milliseconds, mode = AnimationMode.ONCE)
        explosion.advance(250.milliseconds)
        assertFalse(explosion.isFinished)
        explosion.advance(1000.milliseconds)
        assertTrue(explosion.isFinished)
        assertPrints(explosion.frameIndex, "2")
    }

    @Sample
    fun seek() {
        val walk = Animation(frameCount = 4, frameDuration = 100.milliseconds)
        walk.advance(50.milliseconds)
        walk.seek(3)
        assertPrints(walk.frameIndex, "3")
        assertPrints(walk.frameElapsed, "0s")
    }

    @Sample
    fun duration() {
        assertPrints(Animation(frameCount = 4, frameDuration = 100.milliseconds).duration, "400ms")
        // A ping-pong cycle goes there and back: 0, 1, 2, 3, 2, 1.
        val bounce = Animation(frameCount = 4, frameDuration = 100.milliseconds, mode = AnimationMode.PING_PONG)
        assertPrints(bounce.duration, "600ms")
    }

    @Sample
    fun restart() {
        val explosion = Animation(frameCount = 3, frameDuration = 100.milliseconds, mode = AnimationMode.ONCE)
        explosion.advance(1000.milliseconds)
        explosion.restart()
        assertFalse(explosion.isFinished)
        assertPrints(explosion.frameIndex, "0")
    }
}
