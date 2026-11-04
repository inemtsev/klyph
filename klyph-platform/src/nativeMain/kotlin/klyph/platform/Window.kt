package klyph.platform

import cnames.structs.SDL_Cursor
import cnames.structs.SDL_GPUTexture
import cnames.structs.SDL_Window
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import sdl3.SDL_CreateColorCursor
import sdl3.SDL_CreateSurfaceFrom
import sdl3.SDL_CreateWindow
import sdl3.SDL_CursorVisible
import sdl3.SDL_DestroyCursor
import sdl3.SDL_DestroySurface
import sdl3.SDL_DestroyWindow
import sdl3.SDL_GetDefaultCursor
import sdl3.SDL_GetWindowFlags
import sdl3.SDL_GetWindowPixelDensity
import sdl3.SDL_GetWindowSize
import sdl3.SDL_GetWindowSizeInPixels
import sdl3.SDL_HideCursor
import sdl3.SDL_PIXELFORMAT_RGBA32
import sdl3.SDL_SetCursor
import sdl3.SDL_SetWindowFullscreen
import sdl3.SDL_SetWindowFullscreenMode
import sdl3.SDL_ShowCursor
import sdl3.SDL_WINDOW_FULLSCREEN
import sdl3.SDL_WINDOW_HIDDEN
import sdl3.SDL_WINDOW_HIGH_PIXEL_DENSITY
import sdl3.SDL_WINDOW_RESIZABLE

/**
 * Options a [Window] is created with.
 */
@KlyphPlatformApi
public enum class WindowFlag(internal val bit: ULong) {
    /** The user can resize the window. */
    RESIZABLE(SDL_WINDOW_RESIZABLE),

    /** The window's drawable uses the display's full pixel density. */
    HIGH_PIXEL_DENSITY(SDL_WINDOW_HIGH_PIXEL_DENSITY),

    /** The window covers the whole display. */
    FULLSCREEN(SDL_WINDOW_FULLSCREEN),

    /** The window is not shown until it is made visible. */
    HIDDEN(SDL_WINDOW_HIDDEN),
}

/**
 * Whether the mouse cursor is drawn over a [Window].
 */
public enum class CursorMode {
    /** The cursor is drawn: the system arrow, or the image set with [Window.setCursor]. */
    VISIBLE,

    /** The cursor is not drawn while it is over the window. Mouse input still arrives. */
    HIDDEN,
}

/**
 * How a [Window] takes up the screen.
 */
public enum class WindowMode {
    /** A normal window with a title bar, at the size it was created with or resized to. */
    WINDOWED,

    /**
     * A borderless window covering its whole display at the display's own resolution, without a
     * change of video mode, so switching in and out is quick and other windows keep working.
     */
    FULLSCREEN,
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
    /** The cursor made by the last [setCursor], or `null` while the system arrow is in use. */
    private var customCursor: CPointer<SDL_Cursor>? = null

    /** Paths passed to [capture] since the last frame read back. */
    private val queuedCaptures = ArrayList<String>()

    /** The swapchain texture last acquired for this window, and its size in pixels. */
    internal var swapchainTexture: CPointer<SDL_GPUTexture>? = null
    internal var swapchainWidth: Int = 0
    internal var swapchainHeight: Int = 0

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

    /**
     * Whether the mouse cursor is drawn while it is over the window. Starts as
     * [CursorMode.VISIBLE].
     *
     * The platform hides the cursor only while it is over the window; it reappears when it moves
     * onto another window or the desktop, and disappears again when it comes back. The setting is
     * shared by every window of the process, since the platform has one cursor.
     */
    public var cursor: CursorMode
        get() = if (SDL_CursorVisible()) CursorMode.VISIBLE else CursorMode.HIDDEN
        set(value) {
            when (value) {
                CursorMode.VISIBLE -> SDL_ShowCursor()
                CursorMode.HIDDEN -> SDL_HideCursor()
            }
        }

    /**
     * How the window takes up the screen. Setting it switches between a normal window and
     * borderless fullscreen on the display that holds the window.
     *
     * Reading it returns the window's current state, which the platform may change later than the
     * assignment: on macOS the switch to fullscreen animates for about half a second, and the
     * user can leave fullscreen with the window's own controls. Each change of size arrives as a
     * resize event once it has happened.
     *
     * @throws SdlException if the platform refuses the switch.
     */
    public var mode: WindowMode
        get() = if (SDL_GetWindowFlags(handle) and SDL_WINDOW_FULLSCREEN != 0uL) WindowMode.FULLSCREEN else WindowMode.WINDOWED
        set(value) {
            val fullscreen = value == WindowMode.FULLSCREEN
            // No exclusive display mode: fullscreen is the borderless desktop kind.
            if (fullscreen && !SDL_SetWindowFullscreenMode(handle, null)) sdlFailure("SDL_SetWindowFullscreenMode")
            if (!SDL_SetWindowFullscreen(handle, fullscreen)) sdlFailure("SDL_SetWindowFullscreen")
        }

    /**
     * Replaces the cursor with [image] while it is over the window, with the pixel at column
     * [hotspotX] and row [hotspotY] of the image marking the point that clicks land on. The cursor
     * keeps its own copy of the pixels, so later changes to [image] do not reach it.
     *
     * The image replaces the one from an earlier call, which is freed. [resetCursor] goes back to
     * the system arrow, and [close] does so too. [cursor] still decides whether it is drawn. Like
     * [cursor], the image is shared by every window of the process.
     *
     * @throws IllegalArgumentException if the hotspot is outside the image.
     * @throws SdlException if the platform cannot make a cursor from the image.
     */
    public fun setCursor(image: Image, hotspotX: Int, hotspotY: Int) {
        require(hotspotX in 0..<image.width && hotspotY in 0..<image.height) {
            "Cursor hotspot ($hotspotX, $hotspotY) is outside the image size ${image.width}x${image.height}."
        }
        val created = image.pixels.usePinned { pinned ->
            // The surface borrows the pixels; the cursor copies them, so the surface can go right away.
            val surface = SDL_CreateSurfaceFrom(
                image.width,
                image.height,
                SDL_PIXELFORMAT_RGBA32,
                pinned.addressOf(0),
                image.width * 4,
            ) ?: sdlFailure("SDL_CreateSurfaceFrom")
            try {
                SDL_CreateColorCursor(surface, hotspotX, hotspotY) ?: sdlFailure("SDL_CreateColorCursor")
            } finally {
                SDL_DestroySurface(surface)
            }
        }
        if (!SDL_SetCursor(created)) {
            val message = sdlFailureMessage("SDL_SetCursor")
            SDL_DestroyCursor(created)
            throw SdlException(message)
        }
        customCursor?.let { SDL_DestroyCursor(it) }
        customCursor = created
    }

    /**
     * Goes back to the system arrow after [setCursor] and frees the image cursor. Does nothing
     * when no image cursor is set. [cursor] still decides whether the arrow is drawn.
     */
    public fun resetCursor() {
        val custom = customCursor ?: return
        SDL_GetDefaultCursor()?.let { SDL_SetCursor(it) }
        SDL_DestroyCursor(custom)
        customCursor = null
    }

    /**
     * Queues a capture of the next frame drawn for this window and saves it to [path] as a PNG
     * file with four 8-bit channels, every pixel opaque. A relative [path] resolves against
     * [Files.basePath]; a missing directory on the way to the file is created when it is written,
     * as [Files.writeBytes] does.
     *
     * The capture is asynchronous: it applies to the next frame drawn after the call, which the
     * renderer reads back once the GPU has finished it, and the file is written before the frame
     * after that one starts. It covers the whole drawable area, [pixelWidth] by [pixelHeight] pixels, as
     * presented, or the offscreen texture when the frame is drawn into one. Several calls before
     * a frame each get a file of that same frame. The frame that holds a capture waits for the GPU
     * to finish it, so it takes longer than the others.
     *
     * A capture that fails, for example because the file cannot be written, prints one line to
     * standard error through [printError] and is dropped; nothing is thrown or reported anywhere
     * else.
     *
     * Has no sample because it needs a display.
     *
     * @throws IllegalArgumentException if [path] is empty.
     */
    public fun capture(path: String) {
        require(path.isNotEmpty()) { "Capture path is empty." }
        queuedCaptures += path
    }

    /** Returns the paths queued by [capture] and empties the queue, or returns `null` when it is empty. */
    internal fun takeQueuedCapturesOrNull(): List<String>? {
        if (queuedCaptures.isEmpty()) return null
        val paths = queuedCaptures.toList()
        queuedCaptures.clear()
        return paths
    }

    /** Destroys the window and frees the image cursor of [setCursor]. Using it afterwards is an error. */
    override fun close() {
        resetCursor()
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
@KlyphPlatformApi
public fun Window(title: String, width: Int, height: Int, flags: Set<WindowFlag> = emptySet()): Window {
    require(width > 0) { "Window width $width is not positive." }
    require(height > 0) { "Window height $height is not positive." }
    var bits = 0uL
    for (flag in flags) bits = bits or flag.bit
    val handle = SDL_CreateWindow(title, width, height, bits) ?: sdlFailure("SDL_CreateWindow")
    return Window(handle, title)
}
