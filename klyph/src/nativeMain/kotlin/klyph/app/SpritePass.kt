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
import klyph.render.Camera
import klyph.render.Canvas
import klyph.render.DrawSpace
import klyph.render.QuadBuffer
import klyph.render.Sprite
import klyph.render.SpriteRenderer
import klyph.particle.extractParticles
import klyph.text.extractTexts
import kotlin.time.TimeSource

/**
 * The engine's part of [Phase.RENDER]: clears the target, draws every sprite, `Text` component,
 * particle and [Canvas] quad through a [SpriteRenderer], world-space quads through the app's [Camera], then
 * submits the frame.
 *
 * Owns the renderer, whose capacity it grows when the world holds more sprites than fit, and,
 * for [RenderTarget.OFFSCREEN], the texture frames are drawn into, which it recreates when the
 * window's pixel size changes.
 */
internal class SpritePass(
    private val device: GpuDevice,
    private val window: Window,
    private val target: RenderTarget,
    private val gpuSync: GpuSync,
) : AutoCloseable {
    private val colorFormat: TextureFormat =
        if (target == RenderTarget.OFFSCREEN) TextureFormat.RGBA8 else device.swapchainFormat(window)
    private val renderer = SpriteRenderer(device, colorFormat)
    private var offscreenTexture: GpuTexture? = null
    private var offscreenWidth = 0
    private var offscreenHeight = 0

    /** The window's drawable width in pixels, kept current by [resize]; the offscreen texture's size. */
    private var pixelWidth = window.pixelWidth

    /** The window's drawable height in pixels, kept current by [resize]; the offscreen texture's size. */
    private var pixelHeight = window.pixelHeight

    /** Glyph quads of the world's `Text` components and quads of its particles, refilled every frame. */
    private val textQuads = QuadBuffer()

    /** Number of quads the last [render] drew: sprites, text glyphs and canvas items. */
    var spriteCount: Int = 0
        private set

    /** Number of draw calls the last [render] issued. */
    var drawCalls: Int = 0
        private set

    /** Notes that the window's drawable area is now [width] by [height] pixels. */
    fun resize(width: Int, height: Int) {
        pixelWidth = width
        pixelHeight = height
    }

    /**
     * Draws [world]'s sprites and `Text` components through [camera], then the quads queued on
     * [canvas], over [clearColor] and submits the frame, adding the time of each step to
     * [stats]. Entities marked `Interpolated` are drawn [fixedStepAlpha] of the way from their
     * transform at the start of the last fixed step to their current one. While the window has no
     * target to draw into (minimized), skips extraction and drawing and only submits the frame.
     * Empties and closes [canvas] in every case, also when drawing throws.
     *
     * [DrawSpace.SCREEN] quads are drawn with a projection of the target's size in points: its
     * pixel size divided by the window's pixel density.
     */
    fun render(world: World, camera: Camera, canvas: Canvas, clearColor: Color, fixedStepAlpha: Float, stats: FrameStats) {
        try {
            draw(world, camera, canvas, clearColor, fixedStepAlpha, stats)
        } finally {
            canvas.finish()
        }
    }

    private fun draw(world: World, camera: Camera, canvas: Canvas, clearColor: Color, fixedStepAlpha: Float, stats: FrameStats) {
        val clock = TimeSource.Monotonic
        var mark = clock.markNow()

        val commands = device.acquireCommandBuffer()
        val targetTexture = acquireTarget(commands)
        var now = clock.markNow()
        stats.add(FrameStats.ACQUISITION, (now - mark).inWholeNanoseconds)
        mark = now

        var count = 0
        textQuads.clear()
        if (targetTexture != null) {
            world.extractTexts(fixedStepAlpha, textQuads)
            world.extractParticles(textQuads)
            val wanted = world.count(Sprite).toLong() + textQuads.count + canvas.quads.count
            renderer.ensureCapacity(wanted.coerceAtMost(SpriteRenderer.MAX_CAPACITY.toLong()).toInt())
            count = renderer.extract(world, fixedStepAlpha, textQuads, canvas.quads)
        }
        spriteCount = count
        now = clock.markNow()
        stats.add(FrameStats.EXTRACTION, (now - mark).inWholeNanoseconds)
        mark = now

        var calls = 0
        if (targetTexture != null) {
            if (count > 0) renderer.upload(commands)
            val pass = commands.beginRenderPass(targetTexture, clearColor)
            if (count > 0) {
                val density = window.pixelDensity
                val screenWidth = (pixelWidth / density).coerceAtLeast(1f)
                val screenHeight = (pixelHeight / density).coerceAtLeast(1f)
                calls = renderer.draw(commands, pass, camera, screenWidth, screenHeight)
            }
            pass.end()
        }
        drawCalls = calls
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

    override fun close() {
        offscreenTexture?.let { device.release(it) }
        offscreenTexture = null
        renderer.close()
    }

    override fun toString(): String =
        "SpritePass(target=$target, gpuSync=$gpuSync, renderer=$renderer, size=${pixelWidth}x$pixelHeight)"
}
