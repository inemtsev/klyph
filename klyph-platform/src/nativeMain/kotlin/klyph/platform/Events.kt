package klyph.platform

import kotlin.jvm.JvmInline
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
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
import sdl3.SDL_EVENT_KEY_DOWN
import sdl3.SDL_EVENT_KEY_UP
import sdl3.SDL_EVENT_QUIT
import sdl3.SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED
import sdl3.SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED
import sdl3.SDL_Event
import sdl3.SDL_GetWindowFromID
import sdl3.SDL_GetWindowPixelDensity
import sdl3.SDL_PollEvent

/**
 * A keyboard key, identified by SDL's key code.
 *
 * Key codes follow the active keyboard layout, so [A] is the key that types `a`, wherever it sits.
 */
@JvmInline
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
 * An event taken from the platform's event queue by [EventPump.pollOrNull].
 *
 * The set of events is closed, so a `when` over them is exhaustive. The pump skips platform
 * events that have no subclass here.
 *
 * Samples live in the sandbox because they need a display.
 */
public sealed class PlatformEvent {
    /** Asks the application to quit, for example because the user closed the last window. */
    public object Quit : PlatformEvent() {
        override fun toString(): String = "Quit"
    }

    /**
     * Reports that [key] was pressed. [isRepeat] is `true` when the key is being held down and the
     * event comes from key repeat rather than a new press.
     */
    public class KeyDown(public val key: KeyCode, public val isRepeat: Boolean) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is KeyDown && key == other.key && isRepeat == other.isRepeat

        override fun hashCode(): Int = 31 * key.hashCode() + isRepeat.hashCode()

        override fun toString(): String = "KeyDown(key=$key, isRepeat=$isRepeat)"
    }

    /** Reports that [key] was released. */
    public class KeyUp(public val key: KeyCode) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is KeyUp && key == other.key

        override fun hashCode(): Int = key.hashCode()

        override fun toString(): String = "KeyUp(key=$key)"
    }

    /**
     * Reports that a window's drawable size changed to [pixelWidth] by [pixelHeight] pixels.
     *
     * The size is in pixels, not window coordinates, so it also changes when the window moves to a
     * display with a different pixel density.
     */
    public class WindowResized(public val pixelWidth: Int, public val pixelHeight: Int) : PlatformEvent() {
        override fun equals(other: Any?): Boolean =
            other is WindowResized && pixelWidth == other.pixelWidth && pixelHeight == other.pixelHeight

        override fun hashCode(): Int = 31 * pixelWidth + pixelHeight

        override fun toString(): String = "WindowResized(pixelWidth=$pixelWidth, pixelHeight=$pixelHeight)"
    }

    /**
     * Reports that a window's display scale changed, typically because it moved to a display with
     * a different pixel density. [pixelDensity] is the window's [Window.pixelDensity] now.
     *
     * The display scale also covers the system's content scale, so the density can equal the one
     * reported before; compare it with the last known value before acting on it.
     */
    public class WindowDensityChanged(public val pixelDensity: Float) : PlatformEvent() {
        override fun equals(other: Any?): Boolean = other is WindowDensityChanged && pixelDensity == other.pixelDensity

        override fun hashCode(): Int = pixelDensity.hashCode()

        override fun toString(): String = "WindowDensityChanged(pixelDensity=$pixelDensity)"
    }
}

/**
 * Drains SDL's event queue. Owns one native event record, so polling allocates nothing beyond the
 * returned [PlatformEvent]. [close] it when the pump is no longer needed.
 *
 * Samples live in the sandbox because they need a display.
 */
public class EventPump : AutoCloseable {
    private val event = nativeHeap.alloc<SDL_Event>()

    /**
     * Returns the next event from the queue, or `null` when the queue is empty.
     *
     * Events that [PlatformEvent] does not model are discarded, so `null` means no event of
     * interest is waiting. Call it in a loop until it returns `null`, once per frame.
     */
    public fun pollOrNull(): PlatformEvent? {
        while (SDL_PollEvent(event.ptr)) {
            when (event.type) {
                SDL_EVENT_QUIT -> return PlatformEvent.Quit
                SDL_EVENT_KEY_DOWN -> return PlatformEvent.KeyDown(KeyCode(event.key.key), isRepeat = event.key.repeat)
                SDL_EVENT_KEY_UP -> return PlatformEvent.KeyUp(KeyCode(event.key.key))
                SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED ->
                    return PlatformEvent.WindowResized(pixelWidth = event.window.data1, pixelHeight = event.window.data2)
                SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED -> {
                    // The event carries no value; a window destroyed since it was queued is skipped.
                    val window = SDL_GetWindowFromID(event.window.windowID) ?: continue
                    val density = SDL_GetWindowPixelDensity(window)
                    if (density > 0f) return PlatformEvent.WindowDensityChanged(density)
                }
            }
        }
        return null
    }

    override fun close() {
        nativeHeap.free(event)
    }

    override fun toString(): String = "EventPump"
}
