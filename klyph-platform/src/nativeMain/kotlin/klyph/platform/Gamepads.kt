package klyph.platform

import sdl3.SDL_GAMEPAD_AXIS_COUNT
import sdl3.SDL_GAMEPAD_AXIS_LEFTX
import sdl3.SDL_GAMEPAD_AXIS_LEFTY
import sdl3.SDL_GAMEPAD_AXIS_LEFT_TRIGGER
import sdl3.SDL_GAMEPAD_AXIS_RIGHTX
import sdl3.SDL_GAMEPAD_AXIS_RIGHTY
import sdl3.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER
import sdl3.SDL_GAMEPAD_BUTTON_BACK
import sdl3.SDL_GAMEPAD_BUTTON_COUNT
import sdl3.SDL_GAMEPAD_BUTTON_DPAD_DOWN
import sdl3.SDL_GAMEPAD_BUTTON_DPAD_LEFT
import sdl3.SDL_GAMEPAD_BUTTON_DPAD_RIGHT
import sdl3.SDL_GAMEPAD_BUTTON_DPAD_UP
import sdl3.SDL_GAMEPAD_BUTTON_EAST
import sdl3.SDL_GAMEPAD_BUTTON_GUIDE
import sdl3.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER
import sdl3.SDL_GAMEPAD_BUTTON_LEFT_STICK
import sdl3.SDL_GAMEPAD_BUTTON_NORTH
import sdl3.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER
import sdl3.SDL_GAMEPAD_BUTTON_RIGHT_STICK
import sdl3.SDL_GAMEPAD_BUTTON_SOUTH
import sdl3.SDL_GAMEPAD_BUTTON_START
import sdl3.SDL_GAMEPAD_BUTTON_WEST
import sdl3.SDL_JOYSTICK_AXIS_MAX

/**
 * Identifies a connected gamepad for as long as it stays connected. SDL never reuses an id, so a
 * gamepad that is unplugged and plugged back in gets a new one.
 */
public value class GamepadId(public val id: UInt) {
    override fun toString(): String = "GamepadId($id)"
}

/**
 * A gamepad button in SDL's standard layout, named by position rather than by label: [SOUTH] is
 * the bottom face button, A on an Xbox pad and cross on a PlayStation pad.
 *
 * Every button [EventPump] reports has an [index] below [COUNT], so button state can live in a flat
 * array indexed by [index]. Indices without a constant here are extra buttons some pads have, such
 * as paddles, a touchpad press or a share button.
 */
public value class GamepadButton(public val index: Int) {
    override fun toString(): String = "GamepadButton($index)"

    public companion object {
        /** The bottom face button: A on Xbox, cross on PlayStation, B on Nintendo. */
        public val SOUTH: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_SOUTH)
        /** The right face button: B on Xbox, circle on PlayStation, A on Nintendo. */
        public val EAST: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_EAST)
        /** The left face button: X on Xbox, square on PlayStation, Y on Nintendo. */
        public val WEST: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_WEST)
        /** The top face button: Y on Xbox, triangle on PlayStation, X on Nintendo. */
        public val NORTH: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_NORTH)
        /** The back (view, select, minus) button. */
        public val BACK: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_BACK)
        /** The guide (home, logo) button. */
        public val GUIDE: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_GUIDE)
        /** The start (menu, options, plus) button. */
        public val START: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_START)
        /** A press on the left stick. */
        public val LEFT_STICK: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_LEFT_STICK)
        /** A press on the right stick. */
        public val RIGHT_STICK: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_RIGHT_STICK)
        /** The left shoulder button above the left trigger. */
        public val LEFT_SHOULDER: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_LEFT_SHOULDER)
        /** The right shoulder button above the right trigger. */
        public val RIGHT_SHOULDER: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER)
        /** Up on the directional pad. */
        public val DPAD_UP: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_DPAD_UP)
        /** Down on the directional pad. */
        public val DPAD_DOWN: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_DPAD_DOWN)
        /** Left on the directional pad. */
        public val DPAD_LEFT: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_DPAD_LEFT)
        /** Right on the directional pad. */
        public val DPAD_RIGHT: GamepadButton = GamepadButton(SDL_GAMEPAD_BUTTON_DPAD_RIGHT)

        /** The number of gamepad buttons; every button [EventPump] reports has an index below it. */
        public val COUNT: Int = SDL_GAMEPAD_BUTTON_COUNT
    }
}

/**
 * A gamepad axis in SDL's standard layout: the two sticks and the two analog triggers.
 *
 * [PlatformEvent.GamepadAxisMoved] reports stick values from -1 to 1, where negative is left on an
 * X axis and up on a Y axis, and trigger values from 0 (released) to 1 (fully pressed). Every axis
 * [EventPump] reports has an [index] below [COUNT].
 */
public value class GamepadAxis(public val index: Int) {
    override fun toString(): String = "GamepadAxis($index)"

    public companion object {
        /** The left stick's horizontal axis; negative is left. */
        public val LEFT_X: GamepadAxis = GamepadAxis(SDL_GAMEPAD_AXIS_LEFTX)
        /** The left stick's vertical axis; negative is up. */
        public val LEFT_Y: GamepadAxis = GamepadAxis(SDL_GAMEPAD_AXIS_LEFTY)
        /** The right stick's horizontal axis; negative is left. */
        public val RIGHT_X: GamepadAxis = GamepadAxis(SDL_GAMEPAD_AXIS_RIGHTX)
        /** The right stick's vertical axis; negative is up. */
        public val RIGHT_Y: GamepadAxis = GamepadAxis(SDL_GAMEPAD_AXIS_RIGHTY)
        /** The left analog trigger, from 0 (released) to 1 (fully pressed). */
        public val LEFT_TRIGGER: GamepadAxis = GamepadAxis(SDL_GAMEPAD_AXIS_LEFT_TRIGGER)
        /** The right analog trigger, from 0 (released) to 1 (fully pressed). */
        public val RIGHT_TRIGGER: GamepadAxis = GamepadAxis(SDL_GAMEPAD_AXIS_RIGHT_TRIGGER)

        /** The number of gamepad axes; every axis [EventPump] reports has an index below it. */
        public val COUNT: Int = SDL_GAMEPAD_AXIS_COUNT
    }
}

/**
 * Returns SDL's axis reading [raw], from -32768 to 32767, scaled to -1..1. Triggers read 0..32767,
 * so they land in 0..1.
 */
internal fun normalizedAxisValue(raw: Short): Float = (raw / SDL_JOYSTICK_AXIS_MAX.toFloat()).coerceIn(-1f, 1f)
