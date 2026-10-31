package klyph.app

import klyph.platform.Color
import klyph.platform.GpuValidation
import klyph.platform.PresentMode

/**
 * Holds the settings [runApp] opens the window, the GPU device and the frame loop with.
 *
 * Every parameter has a default, so callers pass only what they change, by name:
 * `AppConfig(title = "Shooter", fixedRate = 50)`. [width] and [height] must be positive,
 * [fixedRate] must be between 1 and 1000, and [maxFixedStepsPerFrame] must be at least 1.
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
     * keeps [PresentMode.VSYNC]; check `app.device.supportsPresentMode(app.window, mode)` to find out.
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
) {
    init {
        require(width > 0) { "Window width $width is not positive." }
        require(height > 0) { "Window height $height is not positive." }
        require(fixedRate in 1..MAX_FIXED_RATE) { "Fixed rate $fixedRate is not between 1 and $MAX_FIXED_RATE." }
        require(maxFixedStepsPerFrame >= 1) { "Max fixed steps per frame $maxFixedStepsPerFrame is less than one." }
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
            clearColor == other.clearColor

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
        return result
    }

    /**
     * Returns every setting as `AppConfig(title=klyph, width=1280, ...)`, in constructor order.
     * The format may change.
     */
    override fun toString(): String =
        "AppConfig(title=$title, width=$width, height=$height, fixedRate=$fixedRate, " +
            "maxFixedStepsPerFrame=$maxFixedStepsPerFrame, presentMode=$presentMode, target=$target, " +
            "gpuSync=$gpuSync, validation=$validation, clearColor=$clearColor)"

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
