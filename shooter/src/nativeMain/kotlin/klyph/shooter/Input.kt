package klyph.shooter

import klyph.app.App
import klyph.entity.Transform
import klyph.event.WindowFocusLost
import klyph.input.Gamepad
import klyph.input.Input
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

/** Play scene INPUT system: fills [Game.controls] from the player's input or from the autopilot. */
fun Round.readControls(app: App) {
    if (game.isAutopilot) steerAutopilot(app) else steerFromInput(app)
}

// Menu keys. Every check is for a press this frame, so holding a key does not run through screens.

/** Returns `true` if the title screen should start a game: Space, a left click, or Start or South on a gamepad. */
fun isStartPressed(input: Input): Boolean =
    input.keyboard.isJustPressed(Scancode.SPACE) || input.mouse.isJustPressed(MouseButton.LEFT) ||
        input.isGamepadJustPressed(GamepadButton.START) || input.isGamepadJustPressed(GamepadButton.SOUTH)

/** Returns `true` if the game over screen should start another game: Space or Start. */
fun isRetryPressed(input: Input): Boolean =
    input.keyboard.isJustPressed(Scancode.SPACE) || input.isGamepadJustPressed(GamepadButton.START)

/** Returns `true` if Escape or a gamepad's Start went down: pause during play, resume while paused. */
fun isPausePressed(input: Input): Boolean =
    input.keyboard.isJustPressed(Scancode.ESCAPE) || input.isGamepadJustPressed(GamepadButton.START)

/** Returns `true` if Escape went down: quit from the title screen, back to it from game over. */
fun isEscapePressed(input: Input): Boolean = input.keyboard.isJustPressed(Scancode.ESCAPE)

/**
 * Returns `true` if the play scene should pause: Escape, Start, or the window going to the
 * background. The autopilot never pauses, so demos and smoke runs play on in the background.
 */
fun isPauseRequested(app: App): Boolean =
    !app.game.isAutopilot && (isPausePressed(app.input) || app.events.any<WindowFocusLost>())

private fun Input.isGamepadJustPressed(button: GamepadButton): Boolean {
    val gamepads = gamepads
    for (index in gamepads.indices) {
        if (gamepads[index].isJustPressed(button)) return true
    }
    return false
}

private fun Round.steerFromInput(app: App) {
    val input = app.input
    val controls = game.controls
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
private fun Round.aimHeading(app: App, gamepad: Gamepad?): Float {
    val mouse = app.input.mouse
    if (gamepad != null) {
        val stick = gamepad.rightStick
        if (stick.x != 0f || stick.y != 0f) {
            game.isAimingWithMouse = false
            return headingOf(stick.x, stick.y)
        }
    }
    if (mouse.delta.x != 0f || mouse.delta.y != 0f) game.isAimingWithMouse = true
    if (!game.isAimingWithMouse) return Float.NaN
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

/** Lets go of everything, so a new game does not start with the last game's steering or trigger. */
fun Controls.release() {
    moveX = 0f
    moveY = 0f
    heading = Float.NaN
    isFiring = false
}
