package klyph.shooter

import klyph.app.App
import klyph.event.KeyPressed
import klyph.event.KeyReleased
import klyph.platform.KeyCode
import kotlin.math.sqrt

/**
 * The keys held down right now, built from key events until the engine has input state (M3).
 * Kept in a small array of key codes, so checking a key neither boxes nor allocates.
 */
class HeldKeys {
    private var codes = IntArray(8)
    private var count = 0

    fun press(key: KeyCode) {
        if (key in this) return
        if (count == codes.size) codes = codes.copyOf(count * 2)
        codes[count++] = key.code.toInt()
    }

    fun release(key: KeyCode) {
        val code = key.code.toInt()
        for (i in 0 until count) {
            if (codes[i] == code) {
                codes[i] = codes[--count]
                return
            }
        }
    }

    operator fun contains(key: KeyCode): Boolean {
        val code = key.code.toInt()
        for (i in 0 until count) if (codes[i] == code) return true
        return false
    }

    override fun toString(): String = "HeldKeys(count=$count)"
}

/** What the player asks the ship to do this frame, from the keyboard or the autopilot. */
class Controls {
    /** Horizontal and vertical steering, each in -1..1; the pair is at most 1 long. */
    var moveX = 0f
    var moveY = 0f
    var isFiring = false

    override fun toString(): String = "Controls(move=$moveX, $moveY, firing=$isFiring)"
}

/** INPUT system: tracks held keys, quits on Escape and fills [Game.controls]. */
fun Game.readInput(app: App) {
    app.events.forEach<KeyPressed> { event ->
        keys.press(event.key)
        if (event.key == KeyCode.ESCAPE) app.quit()
    }
    app.events.forEach<KeyReleased> { event -> keys.release(event.key) }
    if (isAutopilot) steerAutopilot(app) else steerFromKeys()
}

private fun Game.steerFromKeys() {
    val x = axis(negative = KeyCode.A in keys || KeyCode.LEFT in keys, positive = KeyCode.D in keys || KeyCode.RIGHT in keys)
    val y = axis(negative = KeyCode.W in keys || KeyCode.UP in keys, positive = KeyCode.S in keys || KeyCode.DOWN in keys)
    controls.setMove(x, y)
    controls.isFiring = KeyCode.SPACE in keys
}

private fun axis(negative: Boolean, positive: Boolean): Float =
    (if (positive) 1f else 0f) - (if (negative) 1f else 0f)

/** Sets the steering to ([x], [y]), shortened to length 1 so diagonals are not faster. */
fun Controls.setMove(x: Float, y: Float) {
    val lengthSquared = x * x + y * y
    val scale = if (lengthSquared > 1f) 1f / sqrt(lengthSquared) else 1f
    moveX = x * scale
    moveY = y * scale
}
