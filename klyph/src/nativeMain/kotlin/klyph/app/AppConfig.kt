package klyph.app

import klyph.audio.AudioConfig
import klyph.platform.Color
import klyph.platform.GpuValidation
import klyph.platform.PresentMode
import klyph.platform.Scancode

/**
 * Holds the settings [runApp] opens the window, the GPU device and the frame loop with.
 *
 * Every parameter has a default, so callers pass only what they change, by name:
 * `AppConfig(title = "Shooter", fixedRate = 50)`. [width] and [height] must be positive,
 * [fixedRate] must be between 1 and 1000, [maxFixedStepsPerFrame] must be at least 1, and
 * [collisionCellSize] must be a positive finite number.
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
     * The audio settings [App.audio] mixes with. With `AudioConfig(enabled = false)` the engine
     * opens no audio device and every audio call does nothing.
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
) {
    init {
        require(width > 0) { "Window width $width is not positive." }
        require(height > 0) { "Window height $height is not positive." }
        require(fixedRate in 1..MAX_FIXED_RATE) { "Fixed rate $fixedRate is not between 1 and $MAX_FIXED_RATE." }
        require(maxFixedStepsPerFrame >= 1) { "Max fixed steps per frame $maxFixedStepsPerFrame is less than one." }
        require(collisionCellSize > 0f && collisionCellSize.isFinite()) {
            "Collision cell size $collisionCellSize is not a positive finite number."
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
            overlayKey == other.overlayKey

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
            "collisionCellSize=$collisionCellSize, overlayKey=$overlayKey)"

    private companion object {
        const val MAX_FIXED_RATE: Int = 1000
    }
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
