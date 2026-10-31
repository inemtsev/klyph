package klyph.app

import klyph.entity.World
import klyph.platform.Color
import klyph.platform.CommandBuffer
import klyph.platform.GpuDevice
import klyph.platform.GpuTexture
import klyph.platform.TextureFormat
import klyph.platform.TextureUsage
import klyph.platform.Window
import klyph.platform.acquireSwapchainTexture
import klyph.platform.beginRenderPass
import klyph.platform.createTexture
import klyph.platform.release
import klyph.platform.waitFor
import klyph.render.Sprite
import klyph.render.SpriteRenderer
import kotlin.time.TimeSource

/**
 * The engine's part of [Phase.RENDER]: clears the target and draws every sprite through a
 * [SpriteRenderer], then submits the frame.
 *
 * Owns the renderer, which it replaces with one of twice the capacity when the world holds more
 * sprites than fit, and, for [RenderTarget.OFFSCREEN], the texture frames are drawn into, which
 * it recreates when the window's pixel size changes.
 */
internal class SpritePass(
    private val device: GpuDevice,
    private val window: Window,
    private val target: RenderTarget,
    private val gpuSync: GpuSync,
) : AutoCloseable {
    private val colorFormat: TextureFormat =
        if (target == RenderTarget.OFFSCREEN) TextureFormat.RGBA8 else device.swapchainFormat(window)
    private var renderer = SpriteRenderer(device, colorFormat, INITIAL_CAPACITY)
    private var offscreenTexture: GpuTexture? = null
    private var offscreenWidth = 0
    private var offscreenHeight = 0

    /** The window's drawable width in pixels, kept current by [resize]. */
    private var pixelWidth = window.pixelWidth

    /** The window's drawable height in pixels, kept current by [resize]. */
    private var pixelHeight = window.pixelHeight

    /** Number of sprites the last [render] drew. */
    var spriteCount: Int = 0
        private set

    /** Notes that the window's drawable area is now [width] by [height] pixels. */
    fun resize(width: Int, height: Int) {
        pixelWidth = width
        pixelHeight = height
    }

    /**
     * Draws [world]'s sprites with [texture] over [clearColor] and submits the frame, adding the
     * time of each step to [stats]. With no [texture], only clears. While the window has no
     * target to draw into (minimized), skips extraction and drawing and only submits the frame.
     */
    fun render(world: World, texture: GpuTexture?, clearColor: Color, stats: FrameStats) {
        val clock = TimeSource.Monotonic
        var mark = clock.markNow()

        val commands = device.acquireCommandBuffer()
        val targetTexture = acquireTarget(commands)
        var now = clock.markNow()
        stats.add(FrameStats.ACQUISITION, (now - mark).inWholeNanoseconds)
        mark = now

        var count = 0
        if (texture != null && targetTexture != null) {
            ensureCapacity(world.count(Sprite))
            renderer.texture = texture
            count = renderer.extract(world)
        }
        spriteCount = count
        now = clock.markNow()
        stats.add(FrameStats.EXTRACTION, (now - mark).inWholeNanoseconds)
        mark = now

        if (targetTexture != null) {
            if (count > 0) renderer.upload(commands)
            val pass = commands.beginRenderPass(targetTexture, clearColor)
            if (count > 0) renderer.draw(commands, pass, pixelWidth.toFloat(), pixelHeight.toFloat())
            pass.end()
        }
        if (gpuSync == GpuSync.WAIT_EACH_FRAME) {
            val fence = commands.submitAndAcquireFence()
            now = clock.markNow()
            stats.add(FrameStats.RECORDING, (now - mark).inWholeNanoseconds)
            mark = now
            device.waitFor(fence)
            device.release(fence)
            stats.add(FrameStats.GPU_WAIT, mark.elapsedNow().inWholeNanoseconds)
        } else {
            commands.submit()
            stats.add(FrameStats.RECORDING, mark.elapsedNow().inWholeNanoseconds)
        }
    }

    /**
     * Returns the texture to draw this frame into: the offscreen texture, recreated if the window
     * changed size, or the swapchain texture, which is `null` while the window is minimized.
     */
    private fun acquireTarget(commands: CommandBuffer): GpuTexture? {
        if (target == RenderTarget.WINDOW) return commands.acquireSwapchainTexture(window)
        val existing = offscreenTexture
        if (existing != null && offscreenWidth == pixelWidth && offscreenHeight == pixelHeight) return existing
        if (pixelWidth <= 0 || pixelHeight <= 0) return null
        existing?.let { device.release(it) }
        val created = device.createTexture(pixelWidth, pixelHeight, colorFormat, TextureUsage.RENDER_TARGET)
        offscreenTexture = created
        offscreenWidth = pixelWidth
        offscreenHeight = pixelHeight
        return created
    }

    /** Replaces the renderer with a larger one if [sprites] do not fit, doubling its capacity until they do. */
    private fun ensureCapacity(sprites: Int) {
        val current = renderer.capacity
        if (sprites <= current) return
        var capacity = current
        while (capacity < sprites) capacity = if (capacity > Int.MAX_VALUE / 2) Int.MAX_VALUE else capacity * 2
        // The GPU may still read the old renderer's buffers; SDL defers their release until it is done.
        renderer.close()
        renderer = SpriteRenderer(device, colorFormat, capacity)
    }

    override fun close() {
        offscreenTexture?.let { device.release(it) }
        offscreenTexture = null
        renderer.close()
    }

    override fun toString(): String =
        "SpritePass(target=$target, gpuSync=$gpuSync, renderer=$renderer, size=${pixelWidth}x$pixelHeight)"

    private companion object {
        const val INITIAL_CAPACITY: Int = 1024
    }
}
