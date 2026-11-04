package klyph.roller

import klyph.app.App
import klyph.event.WindowFocusLost
import klyph.input.Input
import klyph.input.axis
import klyph.input.isJustPressed
import klyph.platform.GamepadButton
import klyph.platform.Scancode
import klyph.platform.WindowMode

/**
 * What the hero is told to do, from the keyboard and gamepads or from the autopilot. Input is
 * read once per frame in INPUT and the hero moves in FIXED_UPDATE, which runs zero or more times
 * a frame, so a jump press is kept in [isJumpQueued] until a fixed step takes it: a press in a
 * frame with no step would otherwise be lost, and one in a frame with two would jump twice.
 */
class Controls {
    /** -1 for full left, 1 for full right. */
    var move = 0f

    /** Whether a jump button is held, which keeps a rising jump going. */
    var isJumpHeld = false

    /** A jump was pressed and no fixed step has taken it yet. */
    var isJumpQueued = false

    /** `true` in the frame a jump button went down, for the menus. */
    var isJumpPressed = false

    /** Returns `true` once for a queued jump press and forgets it. */
    fun takeJump(): Boolean {
        val queued = isJumpQueued
        isJumpQueued = false
        return queued
    }

    override fun toString(): String = "Controls(move=$move, held=$isJumpHeld, queued=$isJumpQueued)"
}

/**
 * App-level INPUT system: reads A/D, the arrows or a left stick or d-pad for moving, and Space,
 * W, Up or a gamepad's south button for jumping. The autopilot fills [Game.controls] itself in
 * the play scene, so here it only lets go of the menu press.
 */
fun readControls(app: App) {
    val game = app.game
    val controls = game.controls
    controls.isJumpPressed = false
    if (game.isAutopilot) return
    val keyboard = app.input.keyboard
    var move = keyboard.axis(Scancode.LEFT, Scancode.RIGHT) + keyboard.axis(Scancode.A, Scancode.D)
    var pressed = keyboard.isJustPressed(Scancode.SPACE) || keyboard.isJustPressed(Scancode.W) || keyboard.isJustPressed(Scancode.UP)
    var held = keyboard.isDown(Scancode.SPACE) || keyboard.isDown(Scancode.W) || keyboard.isDown(Scancode.UP)
    val gamepads = app.input.gamepads
    for (index in gamepads.indices) {
        val gamepad = gamepads[index]
        move += gamepad.leftStick.x
        if (gamepad.isDown(GamepadButton.DPAD_LEFT)) move -= 1f
        if (gamepad.isDown(GamepadButton.DPAD_RIGHT)) move += 1f
        pressed = pressed || gamepad.isJustPressed(GamepadButton.SOUTH)
        held = held || gamepad.isDown(GamepadButton.SOUTH)
    }
    controls.move = move.coerceIn(-1f, 1f)
    controls.isJumpHeld = held
    controls.isJumpPressed = pressed
    if (pressed) controls.isJumpQueued = true
}

// Menu keys. Every check is for a press this frame, so holding a key does not run through screens.

/** Returns `true` if Escape, P or a gamepad's Start went down: pause during play, resume while paused. */
fun isPausePressed(input: Input): Boolean =
    input.keyboard.isJustPressed(Scancode.ESCAPE) || input.keyboard.isJustPressed(Scancode.P) ||
        input.gamepads.isJustPressed(GamepadButton.START)

/** Returns `true` if Escape went down: quit from the title screen, back to it from the end screens. */
fun isEscapePressed(input: Input): Boolean = input.keyboard.isJustPressed(Scancode.ESCAPE)

/** Returns `true` if a jump, Enter or Start went down, which starts and goes on from the screens. */
fun isStartPressed(app: App): Boolean =
    app.game.controls.isJumpPressed || app.input.keyboard.isJustPressed(Scancode.ENTER) ||
        app.input.gamepads.isJustPressed(GamepadButton.START)

/**
 * Returns `true` if the play scene should pause: Escape, P, Start, or the window going to the
 * background. The autopilot never pauses, so smoke runs play on in the background.
 */
fun isPauseRequested(app: App): Boolean =
    !app.game.isAutopilot && (isPausePressed(app.input) || app.events.any<WindowFocusLost>())

/** App-level INPUT system: F11 switches between a window and fullscreen, on any screen. */
fun toggleFullscreen(app: App) {
    if (!app.input.keyboard.isJustPressed(Scancode.F11)) return
    val window = app.window
    window.mode = if (window.mode == WindowMode.FULLSCREEN) WindowMode.WINDOWED else WindowMode.FULLSCREEN
}
