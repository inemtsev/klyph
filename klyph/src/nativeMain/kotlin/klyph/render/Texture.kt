package klyph.render

import klyph.platform.GpuDevice
import klyph.platform.GpuTexture
import klyph.platform.release

/**
 * A GPU texture of [width] by [height] texels that sprites draw from through a [TextureRegion].
 *
 * A `Texture` owns its GPU texture: [close] releases it through the device it was created on.
 * Drawing a sprite whose texture is closed is an error that the renderer does not detect.
 *
 * Has no sample because creating a texture needs a GPU device, which samples cannot open.
 *
 * @see region
 * @see asRegion
 */
public class Texture : AutoCloseable {
    private val device: GpuDevice?
    private val gpuTexture: GpuTexture?
    private var isClosed = false

    /** Width in texels. */
    public val width: Int

    /** Height in texels. */
    public val height: Int

    /**
     * Creates a texture that takes ownership of [handle], a texture of [width] by [height] texels
     * created on [device]. The sizes are not checked against [handle], so they must be the ones
     * it was created with.
     *
     * @throws IllegalArgumentException if [width] or [height] is not positive.
     */
    public constructor(device: GpuDevice, handle: GpuTexture, width: Int, height: Int) {
        require(width > 0) { "Texture width $width is not positive." }
        require(height > 0) { "Texture height $height is not positive." }
        this.device = device
        this.gpuTexture = handle
        this.width = width
        this.height = height
    }

    /**
     * Creates a texture of [width] by [height] texels with no GPU texture behind it, so tests can
     * check region math and draw order without a device. Its internal GPU handle throws and [close]
     * does nothing. Tests only: [GpuTexture] has no public constructor, so no other code can make one.
     */
    internal constructor(width: Int, height: Int) {
        require(width > 0) { "Texture width $width is not positive." }
        require(height > 0) { "Texture height $height is not positive." }
        this.device = null
        this.gpuTexture = null
        this.width = width
        this.height = height
    }

    /**
     * Number of this texture among all textures created in the process, from 0 up, in creation
     * order. Ids are never reused, also after [close], so code that keeps per-texture state in
     * arrays indexed by id (such as [SpriteOrder]) never mistakes one texture for another.
     */
    internal val id: Int = nextId++

    /**
     * The GPU texture sprites sample. The renderer binds it; nothing outside this module needs it.
     *
     * @throws IllegalStateException if this texture was created for tests and has no GPU texture.
     */
    internal val handle: GpuTexture
        get() = gpuTexture ?: throw IllegalStateException("$this was created for tests and has no GPU texture.")

    /** Releases the GPU texture. Calling it again has no effect. */
    override fun close() {
        if (isClosed) return
        isClosed = true
        val texture = gpuTexture ?: return
        device?.release(texture)
    }

    /** Returns `Texture(256x256)`: the size in texels. */
    override fun toString(): String = "Texture(${width}x$height)"

    private companion object {
        /**
         * The [id] of the next texture. A plain counter: the engine creates textures on one
         * thread, and 2^31 textures in one process is out of reach.
         */
        private var nextId = 0
    }
}
