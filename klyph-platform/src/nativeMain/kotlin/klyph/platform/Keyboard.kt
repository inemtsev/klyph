package klyph.platform

import sdl3.SDLK_0
import sdl3.SDLK_1
import sdl3.SDLK_2
import sdl3.SDLK_3
import sdl3.SDLK_4
import sdl3.SDLK_5
import sdl3.SDLK_6
import sdl3.SDLK_7
import sdl3.SDLK_8
import sdl3.SDLK_9
import sdl3.SDLK_A
import sdl3.SDLK_B
import sdl3.SDLK_BACKSPACE
import sdl3.SDLK_C
import sdl3.SDLK_D
import sdl3.SDLK_DOWN
import sdl3.SDLK_E
import sdl3.SDLK_ESCAPE
import sdl3.SDLK_F
import sdl3.SDLK_G
import sdl3.SDLK_H
import sdl3.SDLK_I
import sdl3.SDLK_J
import sdl3.SDLK_K
import sdl3.SDLK_L
import sdl3.SDLK_LALT
import sdl3.SDLK_LCTRL
import sdl3.SDLK_LEFT
import sdl3.SDLK_LSHIFT
import sdl3.SDLK_M
import sdl3.SDLK_N
import sdl3.SDLK_O
import sdl3.SDLK_P
import sdl3.SDLK_Q
import sdl3.SDLK_R
import sdl3.SDLK_RALT
import sdl3.SDLK_RCTRL
import sdl3.SDLK_RETURN
import sdl3.SDLK_RIGHT
import sdl3.SDLK_RSHIFT
import sdl3.SDLK_S
import sdl3.SDLK_SPACE
import sdl3.SDLK_T
import sdl3.SDLK_TAB
import sdl3.SDLK_U
import sdl3.SDLK_UP
import sdl3.SDLK_V
import sdl3.SDLK_W
import sdl3.SDLK_X
import sdl3.SDLK_Y
import sdl3.SDLK_Z
import sdl3.SDL_GetScancodeFromKey
import sdl3.SDL_SCANCODE_0
import sdl3.SDL_SCANCODE_1
import sdl3.SDL_SCANCODE_2
import sdl3.SDL_SCANCODE_3
import sdl3.SDL_SCANCODE_4
import sdl3.SDL_SCANCODE_5
import sdl3.SDL_SCANCODE_6
import sdl3.SDL_SCANCODE_7
import sdl3.SDL_SCANCODE_8
import sdl3.SDL_SCANCODE_9
import sdl3.SDL_SCANCODE_A
import sdl3.SDL_SCANCODE_B
import sdl3.SDL_SCANCODE_BACKSPACE
import sdl3.SDL_SCANCODE_C
import sdl3.SDL_SCANCODE_COUNT
import sdl3.SDL_SCANCODE_D
import sdl3.SDL_SCANCODE_DOWN
import sdl3.SDL_SCANCODE_E
import sdl3.SDL_SCANCODE_ESCAPE
import sdl3.SDL_SCANCODE_F
import sdl3.SDL_SCANCODE_F1
import sdl3.SDL_SCANCODE_F2
import sdl3.SDL_SCANCODE_F3
import sdl3.SDL_SCANCODE_F4
import sdl3.SDL_SCANCODE_F5
import sdl3.SDL_SCANCODE_F6
import sdl3.SDL_SCANCODE_F7
import sdl3.SDL_SCANCODE_F8
import sdl3.SDL_SCANCODE_F9
import sdl3.SDL_SCANCODE_F10
import sdl3.SDL_SCANCODE_F11
import sdl3.SDL_SCANCODE_F12
import sdl3.SDL_SCANCODE_G
import sdl3.SDL_SCANCODE_H
import sdl3.SDL_SCANCODE_I
import sdl3.SDL_SCANCODE_J
import sdl3.SDL_SCANCODE_K
import sdl3.SDL_SCANCODE_L
import sdl3.SDL_SCANCODE_LALT
import sdl3.SDL_SCANCODE_LCTRL
import sdl3.SDL_SCANCODE_LEFT
import sdl3.SDL_SCANCODE_LSHIFT
import sdl3.SDL_SCANCODE_M
import sdl3.SDL_SCANCODE_N
import sdl3.SDL_SCANCODE_O
import sdl3.SDL_SCANCODE_P
import sdl3.SDL_SCANCODE_Q
import sdl3.SDL_SCANCODE_R
import sdl3.SDL_SCANCODE_RALT
import sdl3.SDL_SCANCODE_RCTRL
import sdl3.SDL_SCANCODE_RETURN
import sdl3.SDL_SCANCODE_RIGHT
import sdl3.SDL_SCANCODE_RSHIFT
import sdl3.SDL_SCANCODE_S
import sdl3.SDL_SCANCODE_SPACE
import sdl3.SDL_SCANCODE_T
import sdl3.SDL_SCANCODE_TAB
import sdl3.SDL_SCANCODE_U
import sdl3.SDL_SCANCODE_UNKNOWN
import sdl3.SDL_SCANCODE_UP
import sdl3.SDL_SCANCODE_V
import sdl3.SDL_SCANCODE_W
import sdl3.SDL_SCANCODE_X
import sdl3.SDL_SCANCODE_Y
import sdl3.SDL_SCANCODE_Z

/**
 * A keyboard key, identified by SDL's key code.
 *
 * A key code names the label a key carries under the active keyboard layout, so [A] is the key
 * that types `a`, wherever it sits. Use key codes for text and prompts ("press Q to quit"); use
 * [Scancode] for controls that depend on where a key sits, such as movement. [toScancode] finds
 * the key that carries a label now.
 *
 * Key codes are sparse (printable keys use their Unicode value, the rest have a high bit set), so
 * do not size arrays by them; index by [Scancode] instead.
 */
public value class KeyCode(public val code: UInt) {
    override fun toString(): String = "KeyCode($code)"

    public companion object {
        /** The A key. */
        public val A: KeyCode = KeyCode(SDLK_A)
        /** The B key. */
        public val B: KeyCode = KeyCode(SDLK_B)
        /** The C key. */
        public val C: KeyCode = KeyCode(SDLK_C)
        /** The D key. */
        public val D: KeyCode = KeyCode(SDLK_D)
        /** The E key. */
        public val E: KeyCode = KeyCode(SDLK_E)
        /** The F key. */
        public val F: KeyCode = KeyCode(SDLK_F)
        /** The G key. */
        public val G: KeyCode = KeyCode(SDLK_G)
        /** The H key. */
        public val H: KeyCode = KeyCode(SDLK_H)
        /** The I key. */
        public val I: KeyCode = KeyCode(SDLK_I)
        /** The J key. */
        public val J: KeyCode = KeyCode(SDLK_J)
        /** The K key. */
        public val K: KeyCode = KeyCode(SDLK_K)
        /** The L key. */
        public val L: KeyCode = KeyCode(SDLK_L)
        /** The M key. */
        public val M: KeyCode = KeyCode(SDLK_M)
        /** The N key. */
        public val N: KeyCode = KeyCode(SDLK_N)
        /** The O key. */
        public val O: KeyCode = KeyCode(SDLK_O)
        /** The P key. */
        public val P: KeyCode = KeyCode(SDLK_P)
        /** The Q key. */
        public val Q: KeyCode = KeyCode(SDLK_Q)
        /** The R key. */
        public val R: KeyCode = KeyCode(SDLK_R)
        /** The S key. */
        public val S: KeyCode = KeyCode(SDLK_S)
        /** The T key. */
        public val T: KeyCode = KeyCode(SDLK_T)
        /** The U key. */
        public val U: KeyCode = KeyCode(SDLK_U)
        /** The V key. */
        public val V: KeyCode = KeyCode(SDLK_V)
        /** The W key. */
        public val W: KeyCode = KeyCode(SDLK_W)
        /** The X key. */
        public val X: KeyCode = KeyCode(SDLK_X)
        /** The Y key. */
        public val Y: KeyCode = KeyCode(SDLK_Y)
        /** The Z key. */
        public val Z: KeyCode = KeyCode(SDLK_Z)

        /** The 0 key on the main keyboard row. */
        public val DIGIT_0: KeyCode = KeyCode(SDLK_0)
        /** The 1 key on the main keyboard row. */
        public val DIGIT_1: KeyCode = KeyCode(SDLK_1)
        /** The 2 key on the main keyboard row. */
        public val DIGIT_2: KeyCode = KeyCode(SDLK_2)
        /** The 3 key on the main keyboard row. */
        public val DIGIT_3: KeyCode = KeyCode(SDLK_3)
        /** The 4 key on the main keyboard row. */
        public val DIGIT_4: KeyCode = KeyCode(SDLK_4)
        /** The 5 key on the main keyboard row. */
        public val DIGIT_5: KeyCode = KeyCode(SDLK_5)
        /** The 6 key on the main keyboard row. */
        public val DIGIT_6: KeyCode = KeyCode(SDLK_6)
        /** The 7 key on the main keyboard row. */
        public val DIGIT_7: KeyCode = KeyCode(SDLK_7)
        /** The 8 key on the main keyboard row. */
        public val DIGIT_8: KeyCode = KeyCode(SDLK_8)
        /** The 9 key on the main keyboard row. */
        public val DIGIT_9: KeyCode = KeyCode(SDLK_9)

        /** The up arrow key. */
        public val UP: KeyCode = KeyCode(SDLK_UP)
        /** The down arrow key. */
        public val DOWN: KeyCode = KeyCode(SDLK_DOWN)
        /** The left arrow key. */
        public val LEFT: KeyCode = KeyCode(SDLK_LEFT)
        /** The right arrow key. */
        public val RIGHT: KeyCode = KeyCode(SDLK_RIGHT)

        /** The space bar. */
        public val SPACE: KeyCode = KeyCode(SDLK_SPACE)
        /** The enter (return) key on the main keyboard. */
        public val ENTER: KeyCode = KeyCode(SDLK_RETURN)
        /** The escape key. */
        public val ESCAPE: KeyCode = KeyCode(SDLK_ESCAPE)
        /** The tab key. */
        public val TAB: KeyCode = KeyCode(SDLK_TAB)
        /** The backspace key. */
        public val BACKSPACE: KeyCode = KeyCode(SDLK_BACKSPACE)

        /** The left shift key. */
        public val LEFT_SHIFT: KeyCode = KeyCode(SDLK_LSHIFT)
        /** The right shift key. */
        public val RIGHT_SHIFT: KeyCode = KeyCode(SDLK_RSHIFT)
        /** The left control key. */
        public val LEFT_CTRL: KeyCode = KeyCode(SDLK_LCTRL)
        /** The right control key. */
        public val RIGHT_CTRL: KeyCode = KeyCode(SDLK_RCTRL)
        /** The left alt (option) key. */
        public val LEFT_ALT: KeyCode = KeyCode(SDLK_LALT)
        /** The right alt (option) key. */
        public val RIGHT_ALT: KeyCode = KeyCode(SDLK_RALT)
    }
}

/**
 * A physical key position on the keyboard, identified by SDL's scancode.
 *
 * A scancode names where a key sits, not what it types: [W] is the key where W sits on a US
 * layout, which types `z` on a French layout. Use scancodes for controls that depend on position,
 * such as movement on W, A, S and D; use [KeyCode] for text and prompts that name a key's label.
 *
 * Every scancode SDL reports is below [COUNT], so keyboard state can live in a flat array indexed
 * by [code]. [UNKNOWN] stands for a key SDL cannot identify.
 */
public value class Scancode(public val code: UInt) {
    override fun toString(): String = "Scancode($code)"

    public companion object {
        /**
         * The number of scancodes. Every scancode SDL reports has a [code] below it, so it sizes
         * arrays indexed by scancode.
         */
        public val COUNT: Int = SDL_SCANCODE_COUNT.toInt()

        /** A key SDL cannot identify. */
        public val UNKNOWN: Scancode = Scancode(SDL_SCANCODE_UNKNOWN)

        /** The key where A sits on a US layout. */
        public val A: Scancode = Scancode(SDL_SCANCODE_A)
        /** The key where B sits on a US layout. */
        public val B: Scancode = Scancode(SDL_SCANCODE_B)
        /** The key where C sits on a US layout. */
        public val C: Scancode = Scancode(SDL_SCANCODE_C)
        /** The key where D sits on a US layout. */
        public val D: Scancode = Scancode(SDL_SCANCODE_D)
        /** The key where E sits on a US layout. */
        public val E: Scancode = Scancode(SDL_SCANCODE_E)
        /** The key where F sits on a US layout. */
        public val F: Scancode = Scancode(SDL_SCANCODE_F)
        /** The key where G sits on a US layout. */
        public val G: Scancode = Scancode(SDL_SCANCODE_G)
        /** The key where H sits on a US layout. */
        public val H: Scancode = Scancode(SDL_SCANCODE_H)
        /** The key where I sits on a US layout. */
        public val I: Scancode = Scancode(SDL_SCANCODE_I)
        /** The key where J sits on a US layout. */
        public val J: Scancode = Scancode(SDL_SCANCODE_J)
        /** The key where K sits on a US layout. */
        public val K: Scancode = Scancode(SDL_SCANCODE_K)
        /** The key where L sits on a US layout. */
        public val L: Scancode = Scancode(SDL_SCANCODE_L)
        /** The key where M sits on a US layout. */
        public val M: Scancode = Scancode(SDL_SCANCODE_M)
        /** The key where N sits on a US layout. */
        public val N: Scancode = Scancode(SDL_SCANCODE_N)
        /** The key where O sits on a US layout. */
        public val O: Scancode = Scancode(SDL_SCANCODE_O)
        /** The key where P sits on a US layout. */
        public val P: Scancode = Scancode(SDL_SCANCODE_P)
        /** The key where Q sits on a US layout. */
        public val Q: Scancode = Scancode(SDL_SCANCODE_Q)
        /** The key where R sits on a US layout. */
        public val R: Scancode = Scancode(SDL_SCANCODE_R)
        /** The key where S sits on a US layout. */
        public val S: Scancode = Scancode(SDL_SCANCODE_S)
        /** The key where T sits on a US layout. */
        public val T: Scancode = Scancode(SDL_SCANCODE_T)
        /** The key where U sits on a US layout. */
        public val U: Scancode = Scancode(SDL_SCANCODE_U)
        /** The key where V sits on a US layout. */
        public val V: Scancode = Scancode(SDL_SCANCODE_V)
        /** The key where W sits on a US layout. */
        public val W: Scancode = Scancode(SDL_SCANCODE_W)
        /** The key where X sits on a US layout. */
        public val X: Scancode = Scancode(SDL_SCANCODE_X)
        /** The key where Y sits on a US layout. */
        public val Y: Scancode = Scancode(SDL_SCANCODE_Y)
        /** The key where Z sits on a US layout. */
        public val Z: Scancode = Scancode(SDL_SCANCODE_Z)

        /** The key where 0 sits on the main row of a US layout. */
        public val DIGIT_0: Scancode = Scancode(SDL_SCANCODE_0)
        /** The key where 1 sits on the main row of a US layout. */
        public val DIGIT_1: Scancode = Scancode(SDL_SCANCODE_1)
        /** The key where 2 sits on the main row of a US layout. */
        public val DIGIT_2: Scancode = Scancode(SDL_SCANCODE_2)
        /** The key where 3 sits on the main row of a US layout. */
        public val DIGIT_3: Scancode = Scancode(SDL_SCANCODE_3)
        /** The key where 4 sits on the main row of a US layout. */
        public val DIGIT_4: Scancode = Scancode(SDL_SCANCODE_4)
        /** The key where 5 sits on the main row of a US layout. */
        public val DIGIT_5: Scancode = Scancode(SDL_SCANCODE_5)
        /** The key where 6 sits on the main row of a US layout. */
        public val DIGIT_6: Scancode = Scancode(SDL_SCANCODE_6)
        /** The key where 7 sits on the main row of a US layout. */
        public val DIGIT_7: Scancode = Scancode(SDL_SCANCODE_7)
        /** The key where 8 sits on the main row of a US layout. */
        public val DIGIT_8: Scancode = Scancode(SDL_SCANCODE_8)
        /** The key where 9 sits on the main row of a US layout. */
        public val DIGIT_9: Scancode = Scancode(SDL_SCANCODE_9)

        /** The F1 function key. */
        public val F1: Scancode = Scancode(SDL_SCANCODE_F1)
        /** The F2 function key. */
        public val F2: Scancode = Scancode(SDL_SCANCODE_F2)
        /** The F3 function key. */
        public val F3: Scancode = Scancode(SDL_SCANCODE_F3)
        /** The F4 function key. */
        public val F4: Scancode = Scancode(SDL_SCANCODE_F4)
        /** The F5 function key. */
        public val F5: Scancode = Scancode(SDL_SCANCODE_F5)
        /** The F6 function key. */
        public val F6: Scancode = Scancode(SDL_SCANCODE_F6)
        /** The F7 function key. */
        public val F7: Scancode = Scancode(SDL_SCANCODE_F7)
        /** The F8 function key. */
        public val F8: Scancode = Scancode(SDL_SCANCODE_F8)
        /** The F9 function key. */
        public val F9: Scancode = Scancode(SDL_SCANCODE_F9)
        /** The F10 function key. */
        public val F10: Scancode = Scancode(SDL_SCANCODE_F10)
        /** The F11 function key. */
        public val F11: Scancode = Scancode(SDL_SCANCODE_F11)
        /** The F12 function key. */
        public val F12: Scancode = Scancode(SDL_SCANCODE_F12)

        /** The up arrow key. */
        public val UP: Scancode = Scancode(SDL_SCANCODE_UP)
        /** The down arrow key. */
        public val DOWN: Scancode = Scancode(SDL_SCANCODE_DOWN)
        /** The left arrow key. */
        public val LEFT: Scancode = Scancode(SDL_SCANCODE_LEFT)
        /** The right arrow key. */
        public val RIGHT: Scancode = Scancode(SDL_SCANCODE_RIGHT)

        /** The space bar. */
        public val SPACE: Scancode = Scancode(SDL_SCANCODE_SPACE)
        /** The enter (return) key on the main keyboard. */
        public val ENTER: Scancode = Scancode(SDL_SCANCODE_RETURN)
        /** The escape key. */
        public val ESCAPE: Scancode = Scancode(SDL_SCANCODE_ESCAPE)
        /** The tab key. */
        public val TAB: Scancode = Scancode(SDL_SCANCODE_TAB)
        /** The backspace key. */
        public val BACKSPACE: Scancode = Scancode(SDL_SCANCODE_BACKSPACE)

        /** The left shift key. */
        public val LEFT_SHIFT: Scancode = Scancode(SDL_SCANCODE_LSHIFT)
        /** The right shift key. */
        public val RIGHT_SHIFT: Scancode = Scancode(SDL_SCANCODE_RSHIFT)
        /** The left control key. */
        public val LEFT_CTRL: Scancode = Scancode(SDL_SCANCODE_LCTRL)
        /** The right control key. */
        public val RIGHT_CTRL: Scancode = Scancode(SDL_SCANCODE_RCTRL)
        /** The left alt (option) key. */
        public val LEFT_ALT: Scancode = Scancode(SDL_SCANCODE_LALT)
        /** The right alt (option) key. */
        public val RIGHT_ALT: Scancode = Scancode(SDL_SCANCODE_RALT)
    }
}

/**
 * Returns the scancode of the key that types this key code under the current keyboard layout, or
 * [Scancode.UNKNOWN] when no key on the layout produces it.
 *
 * The answer follows the layout SDL knows now, so it changes when the user switches layouts; look
 * it up again rather than caching it across frames. When several keys produce the key code, the
 * first one SDL finds is returned. It searches SDL's key map, so it is not free, and like every
 * keyboard query it belongs on the thread that started [Subsystem.VIDEO].
 *
 * Samples live in the sandbox because they need a display.
 */
public fun KeyCode.toScancode(): Scancode = Scancode(SDL_GetScancodeFromKey(code, null))
