package klyph.app

import klyph.audio.AudioConfig
import klyph.platform.Color
import klyph.platform.CursorMode
import klyph.platform.GpuValidation
import klyph.platform.PresentMode
import klyph.platform.SamplerFilter
import klyph.platform.Scancode
import klyph.platform.Sdl
import klyph.platform.WindowMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Holds the settings [runApp] opens the window, the GPU device and the frame loop with.
 *
 * Every parameter has a default, so callers pass only what they change, by name:
 * `AppConfig(title = "Shooter", fixedRate = 50)`. [width] and [height] must be positive,
 * [fixedRate] must be between 1 and 1000, [maxFixedStepsPerFrame] must be at least 1,
 * [collisionCellSize] must be a positive finite number, [frameLimit] must be at least 0, and
 * [timeLimit] must not be negative or infinite. [fromEnvironment] fills the limits and a few
 * other settings, among them [windowMode] and [screenshot], from `KLYPH_*` environment variables.
 *
 * Two configs are equal when every setting is equal.
 *
 * @throws IllegalArgumentException if a value is outside its range.
 *
 * @sample samples.app.AppConfigs.appConfig
 */
public class AppConfig(
    /** The window title. */
    public val title: String = "klyph",
    /** The window width in points, which is less than its width in pixels on high-density displays. */
    public val width: Int = 1280,
    /** The window height in points. */
    public val height: Int = 720,
    /** The number of [Phase.FIXED_UPDATE] steps per second of game time, between 1 and 1000. */
    public val fixedRate: Int = 60,
    /**
     * The most [Phase.FIXED_UPDATE] steps one frame runs. After a stall the loop runs at most this
     * many steps and drops the rest of the backlog, so slow frames do not make later frames slower.
     */
    public val maxFixedStepsPerFrame: Int = 4,
    /**
     * How frames reach the display. A mode the window does not support is ignored and the window
     * keeps [PresentMode.VSYNC]; [App.presentMode] tells which mode is in effect.
     */
    public val presentMode: PresentMode = PresentMode.VSYNC,
    /** Where frames are drawn: the window, or an offscreen texture for benchmarks. */
    public val target: RenderTarget = RenderTarget.WINDOW,
    /** Whether each frame waits for the GPU to finish it before the next frame starts. */
    public val gpuSync: GpuSync = GpuSync.NONE,
    /** Whether the GPU backend runs its validation layer, which is slow. */
    public val validation: GpuValidation = GpuValidation.DISABLED,
    /** The color every frame is cleared to before sprites are drawn; [App.clearColor] starts with it. */
    public val clearColor: Color = Color(red = 0.10f, green = 0.12f, blue = 0.18f),
    /**
     * The directory [App.assets] loads relative paths from, or `null` for the `assets/` directory
     * next to the executable. A relative directory resolves against [klyph.platform.Files.basePath].
     */
    public val assetRoot: String? = null,
    /**
     * The audio settings [App.audio] mixes with. With `AudioConfig(output = AudioOutput.NONE)` the
     * engine opens no audio device and every audio call does nothing.
     */
    public val audio: AudioConfig = AudioConfig(),
    /**
     * The width and height in world units of the grid cells [App.collisions] sorts colliders into,
     * a positive finite number. A size near that of a typical collider works best.
     */
    public val collisionCellSize: Float = 64f,
    /**
     * The key that shows and hides [App.overlay], the debug overlay, or `null` for no key, which
     * leaves showing it to the game through `App.overlay.isVisible`. The engine checks it once a
     * frame, after it has updated [App.input] and before any [Phase.INPUT] system runs.
     */
    public val overlayKey: Scancode? = Scancode.F3,
    /** Whether [App.overlay] is shown from the first frame or starts hidden until [overlayKey] goes down. */
    public val overlay: OverlayStart = OverlayStart.HIDDEN,
    /**
     * The number of frames after which [runApp] quits by itself, as [App.quit] would, or `null`,
     * the default, for no limit. At least 0; with 0 no frame runs, only STARTUP and SHUTDOWN.
     */
    public val frameLimit: Int? = null,
    /**
     * The game time after which [runApp] quits by itself: the first frame that brings
     * [Time.elapsed] to it or past it is the last. `null`, the default, sets no limit. Must not be
     * negative or infinite. With both limits, whichever is reached first ends the app.
     */
    public val timeLimit: Duration? = null,
    /**
     * Whether the window opens as a normal window or borderless fullscreen on its display.
     * `App.window.mode` switches it later; [fromEnvironment] sets it from `KLYPH_FULLSCREEN`.
     */
    public val windowMode: WindowMode = WindowMode.WINDOWED,
    /**
     * Whether the mouse cursor is drawn over the window, applied when the window opens.
     * `App.window.cursor` changes it later.
     */
    public val cursor: CursorMode = CursorMode.VISIBLE,
    /**
     * The frame the engine captures into a PNG file through `App.window.capture`, or `null` for
     * none. [fromEnvironment] sets it from `KLYPH_SCREENSHOT=<frame>:<path>`, so a run on CI can
     * save a frame, offscreen too.
     */
    public val screenshot: Screenshot? = null,
    /**
     * The sampling filter [App.assets] gives a texture, sprite sheet or font that is loaded
     * without one. [SamplerFilter.LINEAR], the default, blends neighboring texels and suits
     * painted art. [SamplerFilter.NEAREST] keeps the hard edges of pixel art, so a pixel-art game
     * sets it here once instead of on every loading call. A `filter` argument of a loading call
     * still overrides it for that call. The built-in font, [Assets.whitePixel] and [Assets.disc]
     * keep the filters they document.
     */
    public val textureFilter: SamplerFilter = SamplerFilter.LINEAR,
) {
    init {
        require(width > 0) { "Window width $width is not positive." }
        require(height > 0) { "Window height $height is not positive." }
        require(fixedRate in 1..MAX_FIXED_RATE) { "Fixed rate $fixedRate is not between 1 and $MAX_FIXED_RATE." }
        require(maxFixedStepsPerFrame >= 1) { "Max fixed steps per frame $maxFixedStepsPerFrame is less than one." }
        require(collisionCellSize > 0f && collisionCellSize.isFinite()) {
            "Collision cell size $collisionCellSize is not a positive finite number."
        }
        require(frameLimit == null || frameLimit >= 0) { "Frame limit $frameLimit is less than zero." }
        require(timeLimit == null || (!timeLimit.isNegative() && timeLimit.isFinite())) { "Time limit $timeLimit is negative or infinite." }
    }

    override fun equals(other: Any?): Boolean =
        other is AppConfig &&
            title == other.title &&
            width == other.width &&
            height == other.height &&
            fixedRate == other.fixedRate &&
            maxFixedStepsPerFrame == other.maxFixedStepsPerFrame &&
            presentMode == other.presentMode &&
            target == other.target &&
            gpuSync == other.gpuSync &&
            validation == other.validation &&
            clearColor == other.clearColor &&
            assetRoot == other.assetRoot &&
            audio == other.audio &&
            collisionCellSize == other.collisionCellSize &&
            overlayKey == other.overlayKey &&
            overlay == other.overlay &&
            frameLimit == other.frameLimit &&
            timeLimit == other.timeLimit &&
            windowMode == other.windowMode &&
            cursor == other.cursor &&
            screenshot == other.screenshot &&
            textureFilter == other.textureFilter

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + fixedRate
        result = 31 * result + maxFixedStepsPerFrame
        result = 31 * result + presentMode.hashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + gpuSync.hashCode()
        result = 31 * result + validation.hashCode()
        result = 31 * result + clearColor.hashCode()
        result = 31 * result + assetRoot.hashCode()
        result = 31 * result + audio.hashCode()
        result = 31 * result + collisionCellSize.hashCode()
        result = 31 * result + overlayKey.hashCode()
        result = 31 * result + overlay.hashCode()
        result = 31 * result + frameLimit.hashCode()
        result = 31 * result + timeLimit.hashCode()
        result = 31 * result + windowMode.hashCode()
        result = 31 * result + cursor.hashCode()
        result = 31 * result + screenshot.hashCode()
        result = 31 * result + textureFilter.hashCode()
        return result
    }

    /**
     * Returns every setting as `AppConfig(title=klyph, width=1280, ...)`, in constructor order.
     * The format may change.
     */
    override fun toString(): String =
        "AppConfig(title=$title, width=$width, height=$height, fixedRate=$fixedRate, " +
            "maxFixedStepsPerFrame=$maxFixedStepsPerFrame, presentMode=$presentMode, target=$target, " +
            "gpuSync=$gpuSync, validation=$validation, clearColor=$clearColor, assetRoot=$assetRoot, audio=$audio, " +
            "collisionCellSize=$collisionCellSize, overlayKey=$overlayKey, overlay=$overlay, frameLimit=$frameLimit, " +
            "timeLimit=$timeLimit, windowMode=$windowMode, cursor=$cursor, screenshot=$screenshot, " +
            "textureFilter=$textureFilter)"

    /** Creates configs from the process environment. */
    public companion object {
        private const val MAX_FIXED_RATE: Int = 1000

        /**
         * Returns [base] with the settings that `KLYPH_*` environment variables give replaced, so a
         * run from a script, CI or Gradle can bound and configure itself without code:
         *
         * - `KLYPH_FRAMES=<n>` sets [frameLimit] to `n`, a whole number of at least 0.
         * - `KLYPH_SECONDS=<s>` sets [timeLimit] to `s` seconds, a number of at least 0 such as `2.5`.
         * - `KLYPH_VALIDATION=1` turns [validation] on, `0` off.
         * - `KLYPH_VSYNC=1` sets [presentMode] to [PresentMode.VSYNC], `0` to [PresentMode.IMMEDIATE].
         * - `KLYPH_OFFSCREEN=1` sets [target] to [RenderTarget.OFFSCREEN] and [gpuSync] to
         *   [GpuSync.WAIT_EACH_FRAME], which measures frame cost without the display; `0` sets
         *   [RenderTarget.WINDOW] and keeps [gpuSync].
         * - `KLYPH_OVERLAY=1` sets [overlay] to [OverlayStart.VISIBLE], `0` to [OverlayStart.HIDDEN].
         * - `KLYPH_FULLSCREEN=1` sets [windowMode] to [WindowMode.FULLSCREEN], `0` to [WindowMode.WINDOWED].
         * - `KLYPH_SCREENSHOT=<frame>:<path>` sets [screenshot] to `Screenshot(frame, path)`, such as
         *   `20:/tmp/shot.png`. The value splits at its first colon, so the path may contain colons.
         *   A value without that form, a frame below 1 or an empty path, is ignored and keeps the
         *   screenshot of [base].
         *
         * A variable that is not set, or set to an empty string, keeps the value of [base]. The
         * variables are read through `klyph.platform.Sdl.environmentVariable`, which works before
         * [runApp] starts SDL.
         *
         * @throws IllegalArgumentException if a variable other than `KLYPH_SCREENSHOT` holds a value
         *   outside the ones listed.
         *
         * @sample samples.app.AppConfigs.fromEnvironment
         */
        public fun fromEnvironment(base: AppConfig = AppConfig()): AppConfig = fromEnvironment(base) { Sdl.environmentVariable(it) }

        /** Returns [base] with the settings that the `KLYPH_*` variables [environment] returns replaced, as the public overload describes. */
        internal fun fromEnvironment(base: AppConfig, environment: (String) -> String?): AppConfig {
            fun read(name: String): String? = environment(name)?.ifEmpty { null }
            fun flag(name: String): Boolean? = when (val value = read(name)) {
                null -> null
                "1" -> true
                "0" -> false
                else -> throw IllegalArgumentException("Environment variable $name is '$value', which is neither 0 nor 1.")
            }
            val frames = read("KLYPH_FRAMES")?.let { value ->
                value.toIntOrNull()?.takeIf { it >= 0 }
                    ?: throw IllegalArgumentException("Environment variable KLYPH_FRAMES is '$value', which is not a whole number of at least 0.")
            }
            val seconds = read("KLYPH_SECONDS")?.let { value ->
                value.toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }?.seconds
                    ?: throw IllegalArgumentException("Environment variable KLYPH_SECONDS is '$value', which is not a finite number of at least 0.")
            }
            val validation = flag("KLYPH_VALIDATION")
            val vsync = flag("KLYPH_VSYNC")
            val offscreen = flag("KLYPH_OFFSCREEN")
            val overlay = flag("KLYPH_OVERLAY")
            val fullscreen = flag("KLYPH_FULLSCREEN")
            val screenshot = parseScreenshot(read("KLYPH_SCREENSHOT"))
            return AppConfig(
                title = base.title,
                width = base.width,
                height = base.height,
                fixedRate = base.fixedRate,
                maxFixedStepsPerFrame = base.maxFixedStepsPerFrame,
                presentMode = when (vsync) {
                    null -> base.presentMode
                    true -> PresentMode.VSYNC
                    false -> PresentMode.IMMEDIATE
                },
                target = when (offscreen) {
                    null -> base.target
                    true -> RenderTarget.OFFSCREEN
                    false -> RenderTarget.WINDOW
                },
                gpuSync = if (offscreen == true) GpuSync.WAIT_EACH_FRAME else base.gpuSync,
                validation = when (validation) {
                    null -> base.validation
                    true -> GpuValidation.ENABLED
                    false -> GpuValidation.DISABLED
                },
                clearColor = base.clearColor,
                assetRoot = base.assetRoot,
                audio = base.audio,
                collisionCellSize = base.collisionCellSize,
                overlayKey = base.overlayKey,
                overlay = when (overlay) {
                    null -> base.overlay
                    true -> OverlayStart.VISIBLE
                    false -> OverlayStart.HIDDEN
                },
                frameLimit = frames ?: base.frameLimit,
                timeLimit = seconds ?: base.timeLimit,
                windowMode = when (fullscreen) {
                    null -> base.windowMode
                    true -> WindowMode.FULLSCREEN
                    false -> WindowMode.WINDOWED
                },
                cursor = base.cursor,
                screenshot = screenshot ?: base.screenshot,
                textureFilter = base.textureFilter,
            )
        }
    }
}

/** Names whether the debug overlay, [App.overlay], is shown when the app starts. */
public enum class OverlayStart {
    /** The overlay starts hidden; [AppConfig.overlayKey] or `App.overlay.isVisible` shows it. */
    HIDDEN,

    /** The overlay is shown from the first frame. */
    VISIBLE,
}

/** Names where [runApp] draws each frame. */
public enum class RenderTarget {
    /** Draws into the window's swapchain and presents the frame. */
    WINDOW,

    /**
     * Draws into a texture the size of the window and never presents, so the display's refresh
     * does not pace the loop. Use it with [GpuSync.WAIT_EACH_FRAME] to measure frame cost.
     */
    OFFSCREEN,
}

/** Names how the loop waits for the GPU at the end of each frame. */
public enum class GpuSync {
    /** Does not wait. The GPU works on a frame while the CPU prepares the next one. */
    NONE,

    /**
     * Waits until the GPU has finished each frame before the next one starts, and reports the
     * wait as [FrameStats.gpuWait]. Slower, but it makes the GPU's share of a frame measurable.
     */
    WAIT_EACH_FRAME,
}
