package klyph.benchmarks

import klyph.platform.CommandBuffer
import klyph.platform.EventPump
import klyph.platform.GpuDevice
import klyph.platform.GpuTexture
import klyph.platform.KeyCode
import klyph.platform.Window
import klyph.platform.acquireSwapchainTexture
import klyph.platform.release
import klyph.platform.waitFor

/** A named benchmark that [Main][main] can run from the command line. */
internal class Benchmark(
    /** The command-line argument that selects this benchmark. */
    val name: String,
    /** One line shown in the list of available benchmarks. */
    val description: String,
    /** Runs the benchmark until [BenchmarkContext.runFrames] returns, then prints its results. */
    val run: (BenchmarkContext) -> Unit,
)

/**
 * Everything a benchmark shares with the others: the window, the device and the settings read
 * from the environment. [main] creates it and releases it after the benchmark returns.
 */
internal class BenchmarkContext(
    val device: GpuDevice,
    val window: Window,
    private val events: EventPump,
    /** `KLYPH_SPRITES`, default 10000. */
    val spriteCount: Int,
    /** `KLYPH_FRAMES`; `null` runs until the window closes or escape is pressed. */
    val frameLimit: Int?,
    /**
     * The texture frames are drawn into when `KLYPH_OFFSCREEN=1`, or `null` when frames go to
     * the swapchain. Offscreen frames wait for the GPU, so the display is not in the loop.
     */
    val offscreenTarget: GpuTexture?,
) {
    val offscreen: Boolean get() = offscreenTarget != null

    /** Calls [frame] until the user quits, escape is pressed, or [frameLimit] frames have run. */
    fun runFrames(frame: () -> Unit) {
        var frames = 0
        var running = true
        while (running) {
            while (events.poll()) {
                if (events.isQuit || (events.isKeyDown && events.keyCode == KeyCode.ESCAPE)) running = false
            }
            frame()
            frames++
            if (frameLimit != null && frames >= frameLimit) running = false
        }
    }

    /**
     * Returns the texture to draw this frame into: [offscreenTarget], or the swapchain texture,
     * which is `null` when the window is minimized.
     */
    fun acquireTarget(commands: CommandBuffer): GpuTexture? = offscreenTarget ?: commands.acquireSwapchainTexture(window)

    /**
     * Submits [commands] and ends the timer's next two stages: submission, then the GPU wait.
     * Offscreen, the wait lasts until the GPU has finished the frame; otherwise it is empty.
     */
    fun submit(commands: CommandBuffer, timer: StageTimer) {
        if (offscreen) {
            val fence = commands.submitAndAcquireFence()
            timer.endStage()
            device.waitFor(fence)
            device.release(fence)
        } else {
            commands.submit()
            timer.endStage()
        }
        timer.endStage()
    }
}
