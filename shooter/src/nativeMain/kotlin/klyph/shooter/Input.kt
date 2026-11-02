package klyph.shooter

import klyph.app.App
import klyph.entity.Transform
import klyph.event.KeyPressed
import klyph.event.WindowFocusGained
import klyph.event.WindowFocusLost
import klyph.input.Gamepad
import klyph.input.axis
import klyph.platform.GamepadAxis
import klyph.platform.GamepadButton
import klyph.platform.MouseButton
import klyph.platform.Scancode
import kotlin.math.atan2
import kotlin.math.sqrt

/** What the player asks the ship to do this frame, from the keyboard, mouse and gamepad or from the autopilot. */
class Controls {
    /** Horizontal and vertical steering, each in -1..1; the pair is at most 1 long. */
    var moveX = 0f
    var moveY = 0f

    /** Heading to turn the ship to, in radians clockwise from straight up, or NaN to keep the current one. */
    var heading = Float.NaN

    var isFiring = false

    override fun toString(): String = "Controls(move=$moveX, $moveY, heading=$heading, firing=$isFiring)"
}

/** INPUT system: quits on Escape, pauses while the window is in the background and fills [Game.controls]. */
fun Game.readInput(app: App) {
    if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.quit()
    if (isAutopilot) {
        steerAutopilot(app)
    } else {
        updatePause(app)
        steerFromInput(app)
    }
}

/**
 * Pauses when the window loses focus and resumes when it gets it back or a key is pressed, turning
 * the music down while paused. The autopilot never pauses, so demos and smoke runs play on in the
 * background.
 */
private fun Game.updatePause(app: App) {
    val wasPaused = isPaused
    val events = app.events
    if (events.any<WindowFocusLost>()) {
        isPaused = true
    } else if (isPaused) {
        var isResumed = events.any<WindowFocusGained>()
        events.forEach<KeyPressed> { if (!it.isRepeat) isResumed = true }
        if (isResumed) isPaused = false
    }
    if (isPaused != wasPaused) {
        // The music ducks while paused instead of stopping, so resuming does not restart it.
        app.audio.music.volume = if (isPaused) Tuning.PAUSED_MUSIC_VOLUME else 1f
        println(if (isPaused) "Paused." else "Resumed.")
    }
}

private fun Game.steerFromInput(app: App) {
    val input = app.input
    val gamepad = input.gamepads.firstOrNull()
    var x = input.axis(Scancode.A, Scancode.D) + input.axis(Scancode.LEFT, Scancode.RIGHT)
    var y = input.axis(Scancode.W, Scancode.S) + input.axis(Scancode.UP, Scancode.DOWN)
    if (gamepad != null) {
        val stick = gamepad.leftStick
        x += stick.x
        y += stick.y
    }
    controls.setMove(x, y)
    controls.heading = aimHeading(app, gamepad)
    controls.isFiring = input.keyboard.isDown(Scancode.SPACE) || input.mouse.isDown(MouseButton.LEFT) ||
        gamepad != null && (gamepad.isDown(GamepadButton.SOUTH) || gamepad.axis(GamepadAxis.RIGHT_TRIGGER) > Tuning.TRIGGER_THRESHOLD)
}

/**
 * Returns the heading the right stick points along, or toward the cursor once the mouse has moved,
 * or NaN to keep the current heading. Whichever of the two moved last steers; a released stick
 * leaves the ship as it is, while the cursor keeps the ship facing it as both move.
 */
private fun Game.aimHeading(app: App, gamepad: Gamepad?): Float {
    val mouse = app.input.mouse
    if (gamepad != null) {
        val stick = gamepad.rightStick
        if (stick.x != 0f || stick.y != 0f) {
            isAimingWithMouse = false
            return headingOf(stick.x, stick.y)
        }
    }
    if (mouse.delta.x != 0f || mouse.delta.y != 0f) isAimingWithMouse = true
    if (!isAimingWithMouse) return Float.NaN
    val ship = app.world.get(player, Transform)
    val target = mouse.worldPosition
    val dx = target.x - ship.x
    val dy = target.y - ship.y
    // With the cursor on the ship there is no direction to face.
    return if (dx * dx + dy * dy < Tuning.MIN_AIM_DISTANCE * Tuning.MIN_AIM_DISTANCE) Float.NaN else headingOf(dx, dy)
}

/**
 * Returns the rotation that points the ship's nose along ([dx], [dy]). The art faces up, so
 * rotation 0 is (0, -1), and rotation grows clockwise on screen because y points down.
 */
private fun headingOf(dx: Float, dy: Float): Float = atan2(dx, -dy)

/** Sets the steering to ([x], [y]), shortened to length 1 so diagonals are not faster. */
fun Controls.setMove(x: Float, y: Float) {
    val lengthSquared = x * x + y * y
    val scale = if (lengthSquared > 1f) 1f / sqrt(lengthSquared) else 1f
    moveX = x * scale
    moveY = y * scale
}
