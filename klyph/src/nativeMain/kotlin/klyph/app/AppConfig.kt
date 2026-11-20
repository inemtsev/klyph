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
import kotlin.time.DurationUnit

/**
 * Holds the settings [runApp] opens the window, the GPU device and the frame loop with.
 *
 * Every parameter has a default, so callers pass only what they change, by name:
 * `AppConfig(title = "Shooter", fixedRate = 50)`. [width] and [height] must be positive,
 * [fixedRate] must be between 1 and 1000, [maxFixedStepsPerFrame] must be at least 1,
 * [collisionCellSize] must be a positive finite number, [frameLimit] must be at least 0,
 * [timeLimit] must not be negative or infinite, and [frameDelta] must be above 0, at most
 * 250 milliseconds and at most [maxFixedStepsPerFrame] fixed steps long. [fromEnvironment] fills
 * the limits and a few other settings, among them [frameDelta], [windowMode] and [screenshot],
 * from `KLYPH_*` environment variables.
 *
 * Two configs are equal when every setting is equal.
 *
 * @throws IllegalArgumentException if a value is outside its range, or if [frameDelta] is longer
 *   than [maxFixedStepsPerFrame] fixed steps, each one second divided by [fixedRate].
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
    /**
     * The duration every frame advances game time by, or `null`, the default, for the real time
     * each frame took. With a value, every frame lasts exactly this long: [Time.delta] outside a
     * fixed step equals it and [Time.elapsed] grows by it, so [Phase.FIXED_UPDATE] runs the same
     * steps in the same frames on every run, however fast the machine is, and the [screenshot]s of
     * two runs can be compared. The loop still renders and presents as usual; only the measured
     * duration is replaced. Must be above 0, at most 250 milliseconds, the longest frame [Time]
     * accepts, and at most [maxFixedStepsPerFrame] fixed steps long, each step one second divided
     * by [fixedRate]. A longer frame would run only [maxFixedStepsPerFrame] steps and drop the
     * rest, while [Time.elapsed] still counted all of it. Random sources stay the game's
     * business: a game that wants a run to repeat also seeds its own `Random`,
     * `ParticleEmitter.random` included. [fromEnvironment] sets it from `KLYPH_FRAME_DELTA=<seconds>`.
     */
    public val frameDelta: Duration? = null,
    /**
     * Whether [Assets.region] packs the pictures it loads into shared textures, so sprites that show
     * different files draw in one call. [TextureAtlas.AUTOMATIC], the default, packs them, and each
     * picture looks the same either way. [TextureAtlas.NONE] gives every file its own
     * texture, for comparing the two while debugging. [fromEnvironment] sets it from `KLYPH_ATLAS`.
     */
    public val atlas: TextureAtlas = TextureAtlas.AUTOMATIC,
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
        require(frameDelta == null || (frameDelta.isPositive() && frameDelta <= Time.MAX_FRAME_DELTA)) {
            "Frame delta $frameDelta is not above zero and at most ${Time.MAX_FRAME_DELTA}."
        }
        // Steps are as long as Time.fixedDelta, so a frame this long gives the FixedStepper exactly this many steps and drops none.
        val longestFrame = 1.seconds / fixedRate * maxFixedStepsPerFrame
        require(frameDelta == null || frameDelta <= longestFrame) {
            "Frame delta $frameDelta is longer than $longestFrame, the most that $maxFixedStepsPerFrame fixed steps " +
                "at a fixed rate of $fixedRate cover."
        }
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
            textureFilter == other.textureFilter &&
            frameDelta == other.frameDelta &&
            atlas == other.atlas

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
        result = 31 * result + frameDelta.hashCode()
        result = 31 * result + atlas.hashCode()
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
            "textureFilter=$textureFilter, frameDelta=$frameDelta, atlas=$atlas)"

    /**
     * Returns a config with the settings passed replaced and every other setting of this one kept.
     * It is the one place that copies all of them, so [fromEnvironment] names only the settings an
     * environment variable sets. A setting added to the constructor belongs here, in [equals],
     * [hashCode] and [toString]; `AppConfigTest` fails when one is missing.
     *
     * @throws IllegalArgumentException if a value is outside its range, as for the constructor.
     */
    internal fun copy(
        title: String = this.title,
        width: Int = this.width,
        height: Int = this.height,
        fixedRate: Int = this.fixedRate,
        maxFixedStepsPerFrame: Int = this.maxFixedStepsPerFrame,
        presentMode: PresentMode = this.presentMode,
        target: RenderTarget = this.target,
        gpuSync: GpuSync = this.gpuSync,
        validation: GpuValidation = this.validation,
        clearColor: Color = this.clearColor,
        assetRoot: String? = this.assetRoot,
        audio: AudioConfig = this.audio,
        collisionCellSize: Float = this.collisionCellSize,
        overlayKey: Scancode? = this.overlayKey,
        overlay: OverlayStart = this.overlay,
        frameLimit: Int? = this.frameLimit,
        timeLimit: Duration? = this.timeLimit,
        windowMode: WindowMode = this.windowMode,
        cursor: CursorMode = this.cursor,
        screenshot: Screenshot? = this.screenshot,
        textureFilter: SamplerFilter = this.textureFilter,
        frameDelta: Duration? = this.frameDelta,
        atlas: TextureAtlas = this.atlas,
    ): AppConfig = AppConfig(
        title = title,
        width = width,
        height = height,
        fixedRate = fixedRate,
        maxFixedStepsPerFrame = maxFixedStepsPerFrame,
        presentMode = presentMode,
        target = target,
        gpuSync = gpuSync,
        validation = validation,
        clearColor = clearColor,
        assetRoot = assetRoot,
        audio = audio,
        collisionCellSize = collisionCellSize,
        overlayKey = overlayKey,
        overlay = overlay,
        frameLimit = frameLimit,
        timeLimit = timeLimit,
        windowMode = windowMode,
        cursor = cursor,
        screenshot = screenshot,
        textureFilter = textureFilter,
        frameDelta = frameDelta,
        atlas = atlas,
    )

    /** Creates configs from the process environment. */
    public companion object {
        private const val MAX_FIXED_RATE: Int = 1000

        /**
         * Returns [base] with the settings that `KLYPH_*` environment variables give replaced, so a
         * run from a script, CI or Gradle can bound and configure itself without code:
         *
         * - `KLYPH_FRAMES=<n>` sets [frameLimit] to `n`, a whole number of at least 0.
         * - `KLYPH_SECONDS=<s>` sets [timeLimit] to `s` seconds, a number of at least 0 such as `2.5`.
         * - `KLYPH_FRAME_DELTA=<s>` sets [frameDelta] to `s` seconds, a number such as `0.016` that
         *   [frameDelta] accepts for the [fixedRate] and [maxFixedStepsPerFrame] of [base], so every
         *   frame advances game time by exactly that much.
         * - `KLYPH_VALIDATION=1` turns [validation] on, `0` off.
         * - `KLYPH_VSYNC=1` sets [presentMode] to [PresentMode.VSYNC], `0` to [PresentMode.IMMEDIATE].
         * - `KLYPH_OFFSCREEN=1` sets [target] to [RenderTarget.OFFSCREEN] and [gpuSync] to
         *   [GpuSync.WAIT_EACH_FRAME], which measures frame cost without the display; `0` sets
         *   [RenderTarget.WINDOW] and keeps [gpuSync].
         * - `KLYPH_OVERLAY=1` sets [overlay] to [OverlayStart.VISIBLE], `0` to [OverlayStart.HIDDEN].
         * - `KLYPH_FULLSCREEN=1` sets [windowMode] to [WindowMode.FULLSCREEN], `0` to [WindowMode.WINDOWED].
         * - `KLYPH_ATLAS=0` sets [atlas] to [TextureAtlas.NONE], `1` to [TextureAtlas.AUTOMATIC].
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

        /**
         * Returns the duration a `KLYPH_FRAME_DELTA` [value] of seconds names, such as `0.016`.
         *
         * @throws IllegalArgumentException if [value] is not a number above 0 and at most the longest frame, 0.25.
         */
        private fun parseFrameDelta(value: String): Duration =
            value.toDoubleOrNull()?.takeIf { it > 0.0 }?.seconds?.takeIf { it.isPositive() && it <= Time.MAX_FRAME_DELTA }
                ?: throw IllegalArgumentException(
                    "Environment variable KLYPH_FRAME_DELTA is '$value', which is not a number of seconds above 0 " +
                        "and at most ${Time.MAX_FRAME_DELTA.toDouble(DurationUnit.SECONDS)}.",
                )

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
            val frameDelta = read("KLYPH_FRAME_DELTA")?.let(::parseFrameDelta)
            val validation = flag("KLYPH_VALIDATION")
            val vsync = flag("KLYPH_VSYNC")
            val offscreen = flag("KLYPH_OFFSCREEN")
            val overlay = flag("KLYPH_OVERLAY")
            val fullscreen = flag("KLYPH_FULLSCREEN")
            val atlas = flag("KLYPH_ATLAS")
            val screenshot = parseScreenshot(read("KLYPH_SCREENSHOT"))
            return base.copy(
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
                screenshot = screenshot ?: base.screenshot,
                frameDelta = frameDelta ?: base.frameDelta,
                atlas = when (atlas) {
                    null -> base.atlas
                    true -> TextureAtlas.AUTOMATIC
                    false -> TextureAtlas.NONE
                },
            )
        }
    }
}

/**
 * Names whether [Assets.region] packs the pictures it loads into shared textures, set through
 * [AppConfig.atlas].
 */
public enum class TextureAtlas {
    /**
     * Packs each picture `Assets.region` loads into a shared texture, an atlas page, so sprites
     * that show different files in one layer draw in one call. A picture that cannot share one,
     * because it repeats or is larger than a page, gets its own texture, and each picture looks the
     * same either way.
     */
    AUTOMATIC,

    /**
     * Gives every file `Assets.region` loads its own texture, as `Assets.texture` does. For
     * debugging: compare a frame with and without the atlas, or look at one file's texture alone.
     */
    NONE,
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
