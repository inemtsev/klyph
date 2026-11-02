package klyph.squish

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.event.WindowFocusLost
import klyph.input.Input
import klyph.input.axis
import klyph.platform.GamepadButton
import klyph.platform.MouseButton
import klyph.platform.Scancode

/** Where the swatter is and whether it came down this frame, from the mouse, keyboard and gamepad or from the autopilot. */
class Controls {
    /** The swatter's head, in world units. */
    var cursorX = Tuning.WORLD_WIDTH / 2
    var cursorY = Tuning.WORLD_HEIGHT * 0.75f

    /** `true` in the frame the player swats: a click, Space, Enter or the gamepad's south button. */
    var isSwatPressed = false

    override fun toString(): String = "Controls(cursor=$cursorX, $cursorY, swat=$isSwatPressed)"
}

/**
 * App-level INPUT system: moves the swatter with the mouse, or with the arrow keys, WASD, a stick
 * or the d-pad, and notes a swat. A click swats where the mouse is; the other buttons swat where
 * the swatter is. The autopilot fills [Game.controls] itself in the play scene, so here it only
 * lets go of the button.
 */
fun readControls(app: App) {
    val game = app.game
    val controls = game.controls
    val input = app.input
    val mouse = input.mouse
    controls.isSwatPressed = false
    if (game.isAutopilot) return

    val clicked = mouse.isJustPressed(MouseButton.LEFT)
    if (clicked || mouse.delta.x != 0f || mouse.delta.y != 0f) {
        controls.cursorX = mouse.worldPosition.x
        controls.cursorY = mouse.worldPosition.y
    }
    var x = input.axis(Scancode.LEFT, Scancode.RIGHT) + input.axis(Scancode.A, Scancode.D)
    var y = input.axis(Scancode.UP, Scancode.DOWN) + input.axis(Scancode.W, Scancode.S)
    var pressed = clicked || input.keyboard.isJustPressed(Scancode.SPACE) || input.keyboard.isJustPressed(Scancode.ENTER)
    val gamepads = input.gamepads
    for (index in gamepads.indices) {
        val gamepad = gamepads[index]
        x += gamepad.leftStick.x
        y += gamepad.leftStick.y
        if (gamepad.isDown(GamepadButton.DPAD_LEFT)) x -= 1f
        if (gamepad.isDown(GamepadButton.DPAD_RIGHT)) x += 1f
        if (gamepad.isDown(GamepadButton.DPAD_UP)) y -= 1f
        if (gamepad.isDown(GamepadButton.DPAD_DOWN)) y += 1f
        pressed = pressed || gamepad.isJustPressed(GamepadButton.SOUTH)
    }
    if (x != 0f || y != 0f) {
        val step = Tuning.CURSOR_SPEED * app.time.deltaSeconds
        controls.cursorX += x.coerceIn(-1f, 1f) * step
        controls.cursorY += y.coerceIn(-1f, 1f) * step
    }
    controls.cursorX = controls.cursorX.coerceIn(0f, Tuning.WORLD_WIDTH)
    controls.cursorY = controls.cursorY.coerceIn(0f, Tuning.WORLD_HEIGHT)
    controls.isSwatPressed = pressed
    if (pressed) game.slapTimer = Tuning.SWAT_SLAP_SECONDS
}

// Menu keys. Every check is for a press this frame, so holding a key does not run through screens.

/** Returns `true` if Escape, P or a gamepad's Start went down: pause during play, resume while paused. */
fun isPausePressed(input: Input): Boolean =
    input.keyboard.isJustPressed(Scancode.ESCAPE) || input.keyboard.isJustPressed(Scancode.P) ||
        input.isGamepadJustPressed(GamepadButton.START)

/** Returns `true` if Escape went down: quit from the title screen, back to it from the end screen. */
fun isEscapePressed(input: Input): Boolean = input.keyboard.isJustPressed(Scancode.ESCAPE)

/** Returns `true` if a gamepad's Start went down, which starts and resumes like a swat does. */
fun isStartPressed(input: Input): Boolean = input.isGamepadJustPressed(GamepadButton.START)

/**
 * Returns `true` if the play scene should pause: Escape, P, Start, or the window going to the
 * background. The autopilot never pauses, so smoke runs play on in the background.
 */
fun isPauseRequested(app: App): Boolean =
    !app.game.isAutopilot && (isPausePressed(app.input) || app.events.any<WindowFocusLost>())

private fun Input.isGamepadJustPressed(button: GamepadButton): Boolean {
    for (index in gamepads.indices) {
        if (gamepads[index].isJustPressed(button)) return true
    }
    return false
}
