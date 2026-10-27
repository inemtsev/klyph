package klyph.platform

import kotlin.jvm.JvmInline
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import sdl3.SDLK_ESCAPE
import sdl3.SDL_EVENT_KEY_DOWN
import sdl3.SDL_EVENT_QUIT
import sdl3.SDL_Event
import sdl3.SDL_PollEvent

/**
 * A keyboard key, identified by SDL's key code.
 */
@JvmInline
public value class KeyCode(public val code: UInt) {
    override fun toString(): String = "KeyCode($code)"

    public companion object {
        /** The escape key. */
        public val ESCAPE: KeyCode = KeyCode(SDLK_ESCAPE)
    }
}

/**
 * Drains SDL's event queue. Owns one native event record, so polling allocates nothing.
 * [close] it when the pump is no longer needed.
 *
 * Samples live in the sandbox because they need a display.
 */
public class EventPump : AutoCloseable {
    private val event = nativeHeap.alloc<SDL_Event>()

    /** Returns `true` if an event was taken from the queue; it is then readable through the accessors below. */
    public fun poll(): Boolean = SDL_PollEvent(event.ptr)

    /** `true` when the last polled event asks the application to quit. */
    public val isQuit: Boolean get() = event.type == SDL_EVENT_QUIT

    /** `true` when the last polled event is a key press. */
    public val isKeyDown: Boolean get() = event.type == SDL_EVENT_KEY_DOWN

    /** The key of the last polled keyboard event. Only meaningful when [isKeyDown] is `true`. */
    public val keyCode: KeyCode get() = KeyCode(event.key.key)

    override fun close() {
        nativeHeap.free(event)
    }

    override fun toString(): String = "EventPump"
}
