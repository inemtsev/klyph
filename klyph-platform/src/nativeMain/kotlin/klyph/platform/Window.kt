package klyph.platform

import cnames.structs.SDL_Window
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import sdl3.SDL_CreateWindow
import sdl3.SDL_DestroyWindow
import sdl3.SDL_GetWindowPixelDensity
import sdl3.SDL_GetWindowSize
import sdl3.SDL_GetWindowSizeInPixels
import sdl3.SDL_WINDOW_FULLSCREEN
import sdl3.SDL_WINDOW_HIDDEN
import sdl3.SDL_WINDOW_HIGH_PIXEL_DENSITY
import sdl3.SDL_WINDOW_RESIZABLE

/**
 * Options a [Window] is created with.
 */
public enum class WindowFlag(internal val bit: ULong) {
    RESIZABLE(SDL_WINDOW_RESIZABLE),
    HIGH_PIXEL_DENSITY(SDL_WINDOW_HIGH_PIXEL_DENSITY),
    FULLSCREEN(SDL_WINDOW_FULLSCREEN),
    HIDDEN(SDL_WINDOW_HIDDEN),
}

/**
 * An operating-system window. Create one with the [Window] factory and [close] it when done.
 *
 * Samples live in the sandbox because they need a display.
 */
public class Window internal constructor(
    internal val handle: CPointer<SDL_Window>,
    private val title: String,
) : AutoCloseable {
    /**
     * Width of the client area in points, the logical unit the window was created with. On a
     * high-density display one point covers several pixels; see [pixelDensity].
     */
    public val width: Int
        get() = memScoped {
            val width = alloc<IntVar>()
            SDL_GetWindowSize(handle, width.ptr, null)
            width.value
        }

    /** Height of the client area in points. */
    public val height: Int
        get() = memScoped {
            val height = alloc<IntVar>()
            SDL_GetWindowSize(handle, null, height.ptr)
            height.value
        }

    /** Width of the drawable area in pixels, which exceeds the point size on high-density displays. */
    public val pixelWidth: Int
        get() = memScoped {
            val width = alloc<IntVar>()
            SDL_GetWindowSizeInPixels(handle, width.ptr, null)
            width.value
        }

    /** Height of the drawable area in pixels. */
    public val pixelHeight: Int
        get() = memScoped {
            val height = alloc<IntVar>()
            SDL_GetWindowSizeInPixels(handle, null, height.ptr)
            height.value
        }

    /**
     * Number of pixels per point along each axis: 2 on a Retina display, 1 on a standard one.
     *
     * [pixelWidth] is about [width] times this value. It changes when the window moves to a
     * display with a different density, and it is 1 when the platform cannot report it.
     */
    public val pixelDensity: Float
        get() = SDL_GetWindowPixelDensity(handle).takeIf { it > 0f } ?: 1f

    /** Destroys the window. Using it afterwards is an error. */
    override fun close() {
        SDL_DestroyWindow(handle)
    }

    override fun toString(): String = "Window(title=$title)"
}

/**
 * Creates a window titled [title] with a client area of [width] by [height] points. Both sizes
 * must be positive.
 *
 * @throws IllegalArgumentException if [width] or [height] is not positive.
 * @throws SdlException if the window cannot be created.
 */
public fun Window(title: String, width: Int, height: Int, flags: Set<WindowFlag> = emptySet()): Window {
    require(width > 0) { "Window width $width is not positive." }
    require(height > 0) { "Window height $height is not positive." }
    var bits = 0uL
    for (flag in flags) bits = bits or flag.bit
    val handle = SDL_CreateWindow(title, width, height, bits) ?: sdlFailure("SDL_CreateWindow")
    return Window(handle, title)
}
