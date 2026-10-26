package klyph.sandbox

import klyph.entity.ComponentType
import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.platform.GpuDevice
import klyph.platform.GpuTexture
import klyph.platform.TextureUsage
import klyph.platform.waitFor
import klyph.platform.Window
import klyph.platform.acquireSwapchainTexture
import klyph.platform.beginCopyPass
import klyph.platform.beginRenderPass
import klyph.platform.createTexture
import klyph.platform.createTransferBuffer
import klyph.platform.release
import klyph.platform.uploadToTexture
import klyph.platform.write
import klyph.render.Sprite
import klyph.render.SpriteRenderer
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Straight-line motion in pixels per second, plus spin in radians per second. */
internal class Velocity(var dx: Float, var dy: Float, var spin: Float) {
    companion object : ComponentType<Velocity>("Velocity")
}

/**
 * Moves [spriteCount] sprites around the window and draws them through [SpriteRenderer],
 * timing each stage of the frame. Prints a line per second and a summary at the end.
 */
internal class SpriteBenchmark(
    private val device: GpuDevice,
    private val window: Window,
    spriteCount: Int,
    /** Render into an offscreen texture and wait for the GPU each frame instead of presenting. */
    private val offscreen: Boolean,
) : AutoCloseable {
    private val world = World(spriteCount)
    private val renderer = SpriteRenderer(device, device.swapchainFormat(window), capacity = spriteCount)
    private val texture: GpuTexture = uploadCircleTexture(64)
    private val offscreenTarget: GpuTexture? =
        if (offscreen) device.createTexture(window.pixelWidth, window.pixelHeight, device.swapchainFormat(window), TextureUsage.RENDER_TARGET) else null
    private val clock = TimeSource.Monotonic
    private var lastFrame = clock.markNow()
    private var lastReport = clock.markNow()

    var frames: Int = 0
        private set

    // Accumulators for the current one-second window and for the whole run after warm-up.
    private val window1s = StageTimes()
    private val total = StageTimes()
    private var warmedUp = false

    init {
        renderer.texture = texture
        val random = Random(42)
        val width = window.pixelWidth.toFloat()
        val height = window.pixelHeight.toFloat()
        repeat(spriteCount) {
            val entity = world.create()
            world.add(entity, Transform, Transform(x = random.nextFloat() * width, y = random.nextFloat() * height, rotation = random.nextFloat() * 2f * PI.toFloat()))
            world.add(
                entity,
                Sprite,
                Sprite(
                    width = 24f,
                    height = 24f,
                    color = Color(red = 0.5f + random.nextFloat() * 0.5f, green = 0.5f + random.nextFloat() * 0.5f, blue = 0.5f + random.nextFloat() * 0.5f, alpha = 0.9f),
                ),
            )
            world.add(entity, Velocity, Velocity(dx = random.nextFloat() * 200f - 100f, dy = random.nextFloat() * 200f - 100f, spin = random.nextFloat() * 4f - 2f))
        }
    }

    fun frame() {
        val start = clock.markNow()
        val delta = (start - lastFrame).inWholeNanoseconds / 1e9f
        lastFrame = start
        val width = window.pixelWidth.toFloat()
        val height = window.pixelHeight.toFloat()

        update(delta.coerceAtMost(0.1f), width, height)
        val afterUpdate = clock.markNow()

        val commands = device.acquireCommandBuffer()
        val target = offscreenTarget ?: commands.acquireSwapchainTexture(window)
        val afterAcquire = clock.markNow()

        renderer.extract(world)
        val afterExtract = clock.markNow()

        if (target != null) {
            renderer.upload(commands)
            val pass = commands.beginRenderPass(target, CLEAR_COLOR)
            renderer.draw(commands, pass, width, height)
            pass.end()
        }
        val afterRecord: kotlin.time.TimeMark
        if (offscreen) {
            val fence = commands.submitAndAcquireFence()
            afterRecord = clock.markNow()
            device.waitFor(fence)
            device.release(fence)
        } else {
            commands.submit()
            afterRecord = clock.markNow()
        }
        val end = clock.markNow()

        frames++
        val stage = window1s
        stage.add(
            update = afterUpdate - start,
            acquire = afterAcquire - afterUpdate,
            extract = afterExtract - afterAcquire,
            record = afterRecord - afterExtract,
            gpu = end - afterRecord,
            frame = end - start,
        )
        if (end - lastReport >= 1.seconds) {
            println("sprites=${renderer.spriteCount} ${stage.describe()}")
            if (warmedUp) total.addAll(stage) else warmedUp = true
            stage.reset()
            lastReport = end
        }
    }

    private fun update(delta: Float, width: Float, height: Float) {
        world.forEach(Transform, Velocity) { _, transform, velocity ->
            transform.x += velocity.dx * delta
            transform.y += velocity.dy * delta
            transform.rotation += velocity.spin * delta
            if (transform.x < 0f) { transform.x = 0f; velocity.dx = -velocity.dx }
            if (transform.x > width) { transform.x = width; velocity.dx = -velocity.dx }
            if (transform.y < 0f) { transform.y = 0f; velocity.dy = -velocity.dy }
            if (transform.y > height) { transform.y = height; velocity.dy = -velocity.dy }
        }
    }

    fun printSummary() {
        println("--- summary (after 1 s warm-up, ${total.frames} frames) ---")
        println(if (total.frames == 0) "too short to summarize" else total.describe())
    }

    /** Builds a soft white disc as the sprite texture, so no image decoder is needed. */
    private fun uploadCircleTexture(size: Int): GpuTexture {
        val pixels = ByteArray(size * size * 4)
        val radius = size / 2f
        for (y in 0 until size) for (x in 0 until size) {
            val dx = x + 0.5f - radius
            val dy = y + 0.5f - radius
            val distance = sqrt(dx * dx + dy * dy)
            val coverage = (radius - distance).coerceIn(0f, 1f)
            val i = (y * size + x) * 4
            val value = (255 * coverage).toInt().toByte()
            pixels[i] = value
            pixels[i + 1] = value
            pixels[i + 2] = value
            pixels[i + 3] = value
        }
        val texture = device.createTexture(size, size)
        val staging = device.createTransferBuffer(pixels.size)
        device.write(staging, pixels)
        val commands = device.acquireCommandBuffer()
        val copy = commands.beginCopyPass()
        copy.uploadToTexture(staging, texture, size, size)
        copy.end()
        commands.submit()
        device.release(staging)
        return texture
    }

    override fun close() {
        renderer.close()
        device.release(texture)
        offscreenTarget?.let { device.release(it) }
    }
}

/** Sums of per-stage durations over a number of frames. */
private class StageTimes {
    var frames = 0
    var update = Duration.ZERO
    var acquire = Duration.ZERO
    var extract = Duration.ZERO
    var record = Duration.ZERO
    var gpu = Duration.ZERO
    var frame = Duration.ZERO

    fun add(update: Duration, acquire: Duration, extract: Duration, record: Duration, gpu: Duration, frame: Duration) {
        frames++
        this.update += update
        this.acquire += acquire
        this.extract += extract
        this.record += record
        this.gpu += gpu
        this.frame += frame
    }

    fun addAll(other: StageTimes) {
        frames += other.frames
        update += other.update
        acquire += other.acquire
        extract += other.extract
        record += other.record
        gpu += other.gpu
        frame += other.frame
    }

    fun reset() {
        frames = 0
        update = Duration.ZERO
        acquire = Duration.ZERO
        extract = Duration.ZERO
        record = Duration.ZERO
        gpu = Duration.ZERO
        frame = Duration.ZERO
    }

    fun describe(): String {
        if (frames == 0) return "no frames"
        fun ms(d: Duration) = fixed(d.inWholeMicroseconds / frames / 1000.0, 3)
        val fps = frames / (frame.inWholeMicroseconds / 1e6)
        return "fps=${fixed(fps, 0)} frame=${ms(frame)}ms update=${ms(update)}ms acquire=${ms(acquire)}ms extract=${ms(extract)}ms record+submit=${ms(record)}ms gpuwait=${ms(gpu)}ms"
    }
}

/** Formats [value] with [decimals] digits after the point; Kotlin/Native has no String.format. */
private fun fixed(value: Double, decimals: Int): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = (value * scale + 0.5).toLong()
    val whole = scaled / scale
    if (decimals == 0) return whole.toString()
    val fraction = (scaled % scale).toString().padStart(decimals, '0')
    return "$whole.$fraction"
}
