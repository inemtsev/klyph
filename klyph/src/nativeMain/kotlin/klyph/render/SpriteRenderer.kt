package klyph.render

import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.BlendMode
import klyph.platform.BufferUsage
import klyph.platform.CommandBuffer
import klyph.platform.Cycle
import klyph.platform.GpuBuffer
import klyph.platform.GpuDevice
import klyph.platform.GpuShader
import klyph.platform.GpuTexture
import klyph.platform.GraphicsPipeline
import klyph.platform.RenderPass
import klyph.platform.Sampler
import klyph.platform.SamplerFilter
import klyph.platform.TextureFormat
import klyph.platform.TransferBuffer
import klyph.platform.beginCopyPass
import klyph.platform.bind
import klyph.platform.bindFragmentSampler
import klyph.platform.bindVertexStorageBuffer
import klyph.platform.createBuffer
import klyph.platform.createGraphicsPipeline
import klyph.platform.createSampler
import klyph.platform.createShader
import klyph.platform.createTransferBuffer
import klyph.platform.draw
import klyph.platform.map
import klyph.platform.pushVertexUniforms
import klyph.platform.release
import klyph.platform.unmap
import klyph.platform.uploadToBuffer
import klyph.render.generated.SpriteFragmentShader
import klyph.render.generated.SpriteVertexShader

/**
 * Draws every entity that has a [Transform] and a [Sprite] from one texture in a single draw call.
 *
 * Each frame has three steps so callers can time them: [extract] writes one 64-byte record per
 * sprite straight into mapped staging memory, [upload] copies the records to a GPU storage
 * buffer, and [draw] issues one draw of six vertices per sprite. The vertex shader pulls each
 * record by vertex index; there are no vertex buffers. The shaders come from
 * `src/shaders/sprite.*.hlsl`, compiled by the `compileShaders` task.
 *
 * At most [capacity] sprites are drawn per frame; extra sprites are skipped.
 */
public class SpriteRenderer(
    private val device: GpuDevice,
    colorFormat: TextureFormat,
    public val capacity: Int,
    filter: SamplerFilter = SamplerFilter.LINEAR,
) : AutoCloseable {
    private val storage: GpuBuffer = device.createBuffer(BufferUsage.STORAGE_READ, capacity * SPRITE_RECORD_BYTES)
    private val staging: TransferBuffer = device.createTransferBuffer(capacity * SPRITE_RECORD_BYTES)
    private val vertexShader: GpuShader = device.createShader(SpriteVertexShader)
    private val fragmentShader: GpuShader = device.createShader(SpriteFragmentShader)
    private val pipeline: GraphicsPipeline =
        device.createGraphicsPipeline(vertexShader, fragmentShader, colorFormat, BlendMode.ALPHA)
    private val sampler: Sampler = device.createSampler(filter)
    private val viewProjection = FloatArray(16)

    /** Texture every sprite samples from. Must be set before [draw]. */
    public var texture: GpuTexture? = null

    /** Number of sprites written by the last [extract]. */
    public var spriteCount: Int = 0
        private set

    init {
        require(capacity > 0) { "Sprite capacity $capacity is not positive." }
    }

    /**
     * Writes a record for every sprite in [world] into staging memory and returns how many were
     * written. Allocates nothing.
     */
    public fun extract(world: World): Int {
        val records = device.map(staging, Cycle.CYCLE)
        val limit = capacity
        var count = 0
        world.forEach(Transform, Sprite) { _, transform, sprite ->
            if (count < limit) {
                val base = count * SPRITE_RECORD_FLOATS
                records.set(base, transform.x)
                records.set(base + 1, transform.y)
                records.set(base + 2, transform.rotation)
                records.set(base + 3, 0f)
                records.set(base + 4, sprite.width * transform.scaleX)
                records.set(base + 5, sprite.height * transform.scaleY)
                records.set(base + 6, 0f)
                records.set(base + 7, 0f)
                records.set(base + 8, sprite.u0)
                records.set(base + 9, sprite.v0)
                records.set(base + 10, sprite.u1)
                records.set(base + 11, sprite.v1)
                val color = sprite.color
                records.set(base + 12, color.red)
                records.set(base + 13, color.green)
                records.set(base + 14, color.blue)
                records.set(base + 15, color.alpha)
                count++
            }
        }
        device.unmap(staging)
        spriteCount = count
        return count
    }

    /** Records the copy of the extracted records into GPU memory. Call before any render pass. */
    public fun upload(commands: CommandBuffer) {
        if (spriteCount == 0) return
        val pass = commands.beginCopyPass()
        pass.uploadToBuffer(staging, storage, spriteCount * SPRITE_RECORD_BYTES, Cycle.CYCLE)
        pass.end()
    }

    /**
     * Draws the uploaded sprites into the current [pass] with a view of [viewWidth] by
     * [viewHeight] world units whose origin is the top-left corner.
     *
     * @throws IllegalStateException if [texture] is not set.
     */
    public fun draw(commands: CommandBuffer, pass: RenderPass, viewWidth: Float, viewHeight: Float) {
        val atlas = checkNotNull(texture) { "SpriteRenderer has no texture set." }
        if (spriteCount == 0) return
        orthographic(viewWidth, viewHeight)
        pass.bind(pipeline)
        pass.bindVertexStorageBuffer(0, storage)
        pass.bindFragmentSampler(0, atlas, sampler)
        commands.pushVertexUniforms(0, viewProjection)
        pass.draw(spriteCount * 6)
    }

    private fun orthographic(width: Float, height: Float) {
        val m = viewProjection
        m.fill(0f)
        m[0] = 2f / width
        m[5] = -2f / height
        m[10] = 1f
        m[12] = -1f
        m[13] = 1f
        m[15] = 1f
    }

    override fun close() {
        device.release(pipeline)
        device.release(vertexShader)
        device.release(fragmentShader)
        device.release(sampler)
        device.release(staging)
        device.release(storage)
    }

    override fun toString(): String = "SpriteRenderer(capacity=$capacity, sprites=$spriteCount)"
}
