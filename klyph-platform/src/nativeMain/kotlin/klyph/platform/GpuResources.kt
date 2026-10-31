package klyph.platform

import cnames.structs.SDL_GPUBuffer
import cnames.structs.SDL_GPUGraphicsPipeline
import cnames.structs.SDL_GPUSampler
import cnames.structs.SDL_GPUShader
import cnames.structs.SDL_GPUTexture
import cnames.structs.SDL_GPUTransferBuffer
import kotlin.jvm.JvmInline
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memset
import sdl3.SDL_CreateGPUBuffer
import sdl3.SDL_CreateGPUGraphicsPipeline
import sdl3.SDL_CreateGPUSampler
import sdl3.SDL_CreateGPUShader
import sdl3.SDL_CreateGPUTexture
import sdl3.SDL_CreateGPUTransferBuffer
import sdl3.SDL_GPUBlendFactor
import sdl3.SDL_GPUBlendOp
import sdl3.SDL_GPUBufferCreateInfo
import sdl3.SDL_GPUColorTargetDescription
import sdl3.SDL_GPUFilter
import sdl3.SDL_GPUGraphicsPipelineCreateInfo
import sdl3.SDL_GPUPrimitiveType
import sdl3.SDL_GPUSamplerAddressMode
import sdl3.SDL_GPUSamplerCreateInfo
import sdl3.SDL_GPUSamplerMipmapMode
import sdl3.SDL_GPUShaderCreateInfo
import sdl3.SDL_GPUShaderStage
import sdl3.SDL_GPUTextureCreateInfo
import sdl3.SDL_GPUTextureType
import sdl3.SDL_GPUTransferBufferCreateInfo
import sdl3.SDL_GPUTransferBufferUsage
import sdl3.SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ
import sdl3.SDL_GPU_BUFFERUSAGE_INDEX
import sdl3.SDL_GPU_BUFFERUSAGE_VERTEX
import sdl3.SDL_GPU_TEXTUREUSAGE_COLOR_TARGET
import sdl3.SDL_GPU_TEXTUREUSAGE_SAMPLER
import sdl3.SDL_MapGPUTransferBuffer
import sdl3.SDL_ReleaseGPUBuffer
import sdl3.SDL_ReleaseGPUGraphicsPipeline
import sdl3.SDL_ReleaseGPUSampler
import sdl3.SDL_ReleaseGPUShader
import sdl3.SDL_ReleaseGPUTexture
import sdl3.SDL_ReleaseGPUTransferBuffer
import sdl3.SDL_UnmapGPUTransferBuffer

// GPU resources are plain handles, released through the device that created them, as in SDL.

/**
 * A texture owned by the GPU. Swapchain textures are valid only for the frame they were acquired
 * in; created textures live until [GpuDevice.release].
 */
@JvmInline
public value class GpuTexture internal constructor(internal val handle: CPointer<SDL_GPUTexture>) {
    override fun toString(): String = "GpuTexture"
}

/**
 * What a [GpuBuffer] is bound as.
 */
public enum class BufferUsage(internal val bits: UInt) {
    VERTEX(SDL_GPU_BUFFERUSAGE_VERTEX),
    INDEX(SDL_GPU_BUFFERUSAGE_INDEX),

    /** Read by shaders as a storage buffer; the vertex-pulling path uses this. */
    STORAGE_READ(SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ),
}

/**
 * A buffer in GPU memory. Fill it through a [TransferBuffer] and a [CopyPass].
 */
@JvmInline
public value class GpuBuffer internal constructor(internal val handle: CPointer<SDL_GPUBuffer>) {
    override fun toString(): String = "GpuBuffer"
}

/**
 * CPU-visible staging memory used to move data to the GPU. Map it, write, unmap, then upload in a [CopyPass].
 */
@JvmInline
public value class TransferBuffer internal constructor(internal val handle: CPointer<SDL_GPUTransferBuffer>) {
    override fun toString(): String = "TransferBuffer"
}

/**
 * Mapped transfer-buffer memory, addressed as 32-bit floats. Valid until [GpuDevice.unmap].
 * The setters inline to plain stores, so filling a buffer allocates nothing.
 */
@JvmInline
public value class MappedFloats @PublishedApi internal constructor(
    @PublishedApi internal val address: CPointer<FloatVar>,
) {
    /** Writes [value] at float [index]. No bounds check: the caller owns the buffer size. */
    @Suppress("NOTHING_TO_INLINE")
    public inline fun set(index: Int, value: Float) {
        address[index] = value
    }

    override fun toString(): String = "MappedFloats"
}

/**
 * How a sampler picks texels when a texture is scaled.
 */
public enum class SamplerFilter(internal val raw: SDL_GPUFilter) {
    /** Picks the closest texel. Right for pixel art. */
    NEAREST(SDL_GPUFilter.SDL_GPU_FILTER_NEAREST),

    /** Blends the surrounding texels. */
    LINEAR(SDL_GPUFilter.SDL_GPU_FILTER_LINEAR),
}

/**
 * Sampling state for textures: filtering and clamped addressing.
 */
@JvmInline
public value class Sampler internal constructor(internal val handle: CPointer<SDL_GPUSampler>) {
    override fun toString(): String = "Sampler"
}

/**
 * Pipeline stage a [GpuShader] runs in.
 */
public enum class ShaderStage(internal val raw: SDL_GPUShaderStage) {
    VERTEX(SDL_GPUShaderStage.SDL_GPU_SHADERSTAGE_VERTEX),
    FRAGMENT(SDL_GPUShaderStage.SDL_GPU_SHADERSTAGE_FRAGMENT),
}

/**
 * A compiled shader stage.
 */
@JvmInline
public value class GpuShader internal constructor(internal val handle: CPointer<SDL_GPUShader>) {
    override fun toString(): String = "GpuShader"
}

/**
 * How a pipeline combines its output with what is already in the color target.
 */
public enum class BlendMode {
    /** Output replaces the target. */
    NONE,

    /** Straight alpha: `out = src * src.a + dst * (1 - src.a)`. */
    ALPHA,

    /** Premultiplied alpha: `out = src + dst * (1 - src.a)`. */
    PREMULTIPLIED,

    /** Adds the output to the target. */
    ADDITIVE,
}

/**
 * A graphics pipeline: shaders plus fixed-function state, for triangle lists without vertex
 * buffers. Vertex data is pulled from storage buffers by vertex index.
 */
@JvmInline
public value class GraphicsPipeline internal constructor(internal val handle: CPointer<SDL_GPUGraphicsPipeline>) {
    override fun toString(): String = "GraphicsPipeline"
}

/**
 * Creates a buffer of [sizeInBytes] bytes for [usage].
 *
 * @throws IllegalArgumentException if [sizeInBytes] is not positive.
 * @throws SdlException if the GPU cannot allocate it.
 */
public fun GpuDevice.createBuffer(usage: BufferUsage, sizeInBytes: Int): GpuBuffer = memScoped {
    require(sizeInBytes > 0) { "Buffer size $sizeInBytes is not positive." }
    val info = alloc<SDL_GPUBufferCreateInfo>().zeroed()
    info.usage = usage.bits
    info.size = sizeInBytes.toUInt()
    GpuBuffer(SDL_CreateGPUBuffer(handle, info.ptr) ?: sdlFailure("SDL_CreateGPUBuffer"))
}

/** Frees [buffer]. Work already submitted may still read it; SDL defers the actual release. */
public fun GpuDevice.release(buffer: GpuBuffer) {
    SDL_ReleaseGPUBuffer(handle, buffer.handle)
}

/**
 * Creates an upload staging buffer of [sizeInBytes] bytes.
 *
 * @throws IllegalArgumentException if [sizeInBytes] is not positive.
 * @throws SdlException if it cannot be allocated.
 */
public fun GpuDevice.createTransferBuffer(sizeInBytes: Int): TransferBuffer = memScoped {
    require(sizeInBytes > 0) { "Transfer buffer size $sizeInBytes is not positive." }
    val info = alloc<SDL_GPUTransferBufferCreateInfo>().zeroed()
    info.usage = SDL_GPUTransferBufferUsage.SDL_GPU_TRANSFERBUFFERUSAGE_UPLOAD
    info.size = sizeInBytes.toUInt()
    TransferBuffer(SDL_CreateGPUTransferBuffer(handle, info.ptr) ?: sdlFailure("SDL_CreateGPUTransferBuffer"))
}

/** Frees [buffer]. */
public fun GpuDevice.release(buffer: TransferBuffer) {
    SDL_ReleaseGPUTransferBuffer(handle, buffer.handle)
}

/**
 * Whether a map or upload may hand out fresh memory when the previous contents are still in use
 * by the GPU. Cycling avoids a stall but discards the old contents.
 */
public enum class Cycle {
    /** Reuse the same memory. Correct only when the GPU is done with it. */
    KEEP,

    /** Take fresh memory if the old is busy. Use for data rewritten every frame. */
    CYCLE,
}

/**
 * Maps [buffer] for writing and returns its memory as floats. Call [unmap] before uploading.
 *
 * @throws SdlException if mapping fails.
 */
public fun GpuDevice.map(buffer: TransferBuffer, cycle: Cycle): MappedFloats {
    val address = SDL_MapGPUTransferBuffer(handle, buffer.handle, cycle == Cycle.CYCLE)
        ?: sdlFailure("SDL_MapGPUTransferBuffer")
    return MappedFloats(address.reinterpret())
}

/** Ends a mapping started with [map]. The [MappedFloats] handed out is invalid afterwards. */
public fun GpuDevice.unmap(buffer: TransferBuffer) {
    SDL_UnmapGPUTransferBuffer(handle, buffer.handle)
}

/**
 * Copies [bytes] into [buffer] at byte [offset]. Convenient for one-off uploads such as texture
 * pixels; per-frame data should be written through [map] instead to avoid the extra copy.
 *
 * @throws SdlException if mapping fails.
 */
public fun GpuDevice.write(buffer: TransferBuffer, bytes: ByteArray, offset: Int = 0) {
    val address = SDL_MapGPUTransferBuffer(handle, buffer.handle, false)
        ?: sdlFailure("SDL_MapGPUTransferBuffer")
    bytes.usePinned { pinned ->
        platform.posix.memcpy(
            address.reinterpret<kotlinx.cinterop.ByteVar>() + offset,
            pinned.addressOf(0),
            bytes.size.convert(),
        )
    }
    SDL_UnmapGPUTransferBuffer(handle, buffer.handle)
}

/**
 * What a created texture is used for.
 */
public enum class TextureUsage(internal val bits: UInt) {
    /** Read by shaders through a sampler. */
    SAMPLED(SDL_GPU_TEXTUREUSAGE_SAMPLER),

    /** Drawn into by a render pass, and also readable by shaders. */
    RENDER_TARGET(SDL_GPU_TEXTUREUSAGE_COLOR_TARGET or SDL_GPU_TEXTUREUSAGE_SAMPLER),
}

/**
 * Creates a 2D texture of [width] by [height] texels in [format] for [usage].
 *
 * @throws IllegalArgumentException if a dimension is not positive.
 * @throws SdlException if the texture cannot be created.
 */
public fun GpuDevice.createTexture(
    width: Int,
    height: Int,
    format: TextureFormat = TextureFormat.RGBA8,
    usage: TextureUsage = TextureUsage.SAMPLED,
): GpuTexture =
    memScoped {
        require(width > 0) { "Texture width $width is not positive." }
        require(height > 0) { "Texture height $height is not positive." }
        val info = alloc<SDL_GPUTextureCreateInfo>().zeroed()
        info.type = SDL_GPUTextureType.SDL_GPU_TEXTURETYPE_2D
        info.format = format.raw
        info.usage = usage.bits
        info.width = width.toUInt()
        info.height = height.toUInt()
        info.layer_count_or_depth = 1u
        info.num_levels = 1u
        GpuTexture(SDL_CreateGPUTexture(handle, info.ptr) ?: sdlFailure("SDL_CreateGPUTexture"))
    }

/**
 * Creates an [RGBA8][TextureFormat.RGBA8] texture of the size of [image] for
 * [sampling][TextureUsage.SAMPLED] and uploads its pixels.
 *
 * The upload is submitted on its own command buffer before this function returns, so the texture
 * can be drawn with right away. Later changes to [image] do not reach the texture.
 *
 * @throws SdlException if the texture or its staging memory cannot be created, or the upload
 *   cannot be submitted.
 */
public fun GpuDevice.createTexture(image: Image): GpuTexture {
    val texture = createTexture(image.width, image.height)
    try {
        val staging = createTransferBuffer(image.pixels.size)
        try {
            write(staging, image.pixels)
            val commands = acquireCommandBuffer()
            val copy = commands.beginCopyPass()
            copy.uploadToTexture(staging, texture, image.width, image.height)
            copy.end()
            commands.submit()
        } finally {
            // SDL defers the release until the submitted copy has finished with it.
            release(staging)
        }
    } catch (e: Throwable) {
        release(texture)
        throw e
    }
    return texture
}

/** Frees [texture]. Never call this on a swapchain texture. */
public fun GpuDevice.release(texture: GpuTexture) {
    SDL_ReleaseGPUTexture(handle, texture.handle)
}

/**
 * Creates a sampler using [filter] for both minification and magnification, with coordinates
 * clamped to the texture edge.
 *
 * @throws SdlException if the sampler cannot be created.
 */
public fun GpuDevice.createSampler(filter: SamplerFilter): Sampler = memScoped {
    val info = alloc<SDL_GPUSamplerCreateInfo>().zeroed()
    info.min_filter = filter.raw
    info.mag_filter = filter.raw
    info.mipmap_mode = SDL_GPUSamplerMipmapMode.SDL_GPU_SAMPLERMIPMAPMODE_NEAREST
    info.address_mode_u = SDL_GPUSamplerAddressMode.SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE
    info.address_mode_v = SDL_GPUSamplerAddressMode.SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE
    info.address_mode_w = SDL_GPUSamplerAddressMode.SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE
    Sampler(SDL_CreateGPUSampler(handle, info.ptr) ?: sdlFailure("SDL_CreateGPUSampler"))
}

/** Frees [sampler]. */
public fun GpuDevice.release(sampler: Sampler) {
    SDL_ReleaseGPUSampler(handle, sampler.handle)
}

/**
 * Creates a shader for [stage] from [code] in [format]. The resource counts must match what the
 * shader declares, or binding at draw time fails.
 *
 * @throws IllegalArgumentException if [code] is empty or a count is negative.
 * @throws SdlException if the backend rejects the shader.
 */
public fun GpuDevice.createShader(
    code: ByteArray,
    format: ShaderFormat,
    stage: ShaderStage,
    entryPoint: String,
    samplers: Int = 0,
    storageTextures: Int = 0,
    storageBuffers: Int = 0,
    uniformBuffers: Int = 0,
): GpuShader = memScoped {
    require(code.isNotEmpty()) { "Shader code is empty." }
    require(samplers >= 0 && storageTextures >= 0 && storageBuffers >= 0 && uniformBuffers >= 0) {
        "Shader resource counts must not be negative."
    }
    val info = alloc<SDL_GPUShaderCreateInfo>().zeroed()
    info.entrypoint = entryPoint.cstr.ptr
    info.format = format.bit
    info.stage = stage.raw
    info.num_samplers = samplers.toUInt()
    info.num_storage_textures = storageTextures.toUInt()
    info.num_storage_buffers = storageBuffers.toUInt()
    info.num_uniform_buffers = uniformBuffers.toUInt()
    code.usePinned { pinned ->
        info.code = pinned.addressOf(0).reinterpret()
        info.code_size = code.size.convert()
        GpuShader(SDL_CreateGPUShader(handle, info.ptr) ?: sdlFailure("SDL_CreateGPUShader"))
    }
}

/** Frees [shader]. Pipelines built from it keep working. */
public fun GpuDevice.release(shader: GpuShader) {
    SDL_ReleaseGPUShader(handle, shader.handle)
}

/**
 * Creates a pipeline drawing triangle lists with [vertex] and [fragment] into a single color
 * target of [colorFormat], blended with [blend]. No vertex buffers: vertex data is pulled from
 * storage buffers by vertex index.
 *
 * @throws SdlException if the backend rejects the pipeline.
 */
public fun GpuDevice.createGraphicsPipeline(
    vertex: GpuShader,
    fragment: GpuShader,
    colorFormat: TextureFormat,
    blend: BlendMode,
): GraphicsPipeline = memScoped {
    val target = alloc<SDL_GPUColorTargetDescription>().zeroed()
    target.format = colorFormat.raw
    if (blend != BlendMode.NONE) {
        target.blend_state.enable_blend = true
        target.blend_state.color_blend_op = SDL_GPUBlendOp.SDL_GPU_BLENDOP_ADD
        target.blend_state.alpha_blend_op = SDL_GPUBlendOp.SDL_GPU_BLENDOP_ADD
        target.blend_state.src_color_blendfactor = when (blend) {
            BlendMode.ALPHA -> SDL_GPUBlendFactor.SDL_GPU_BLENDFACTOR_SRC_ALPHA
            else -> SDL_GPUBlendFactor.SDL_GPU_BLENDFACTOR_ONE
        }
        target.blend_state.dst_color_blendfactor = when (blend) {
            BlendMode.ADDITIVE -> SDL_GPUBlendFactor.SDL_GPU_BLENDFACTOR_ONE
            else -> SDL_GPUBlendFactor.SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA
        }
        target.blend_state.src_alpha_blendfactor = SDL_GPUBlendFactor.SDL_GPU_BLENDFACTOR_ONE
        target.blend_state.dst_alpha_blendfactor = SDL_GPUBlendFactor.SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA
    }
    val info = alloc<SDL_GPUGraphicsPipelineCreateInfo>().zeroed()
    info.vertex_shader = vertex.handle
    info.fragment_shader = fragment.handle
    info.primitive_type = SDL_GPUPrimitiveType.SDL_GPU_PRIMITIVETYPE_TRIANGLELIST
    info.target_info.color_target_descriptions = target.ptr
    info.target_info.num_color_targets = 1u
    GraphicsPipeline(SDL_CreateGPUGraphicsPipeline(handle, info.ptr) ?: sdlFailure("SDL_CreateGPUGraphicsPipeline"))
}

/** Frees [pipeline]. */
public fun GpuDevice.release(pipeline: GraphicsPipeline) {
    SDL_ReleaseGPUGraphicsPipeline(handle, pipeline.handle)
}

/** Zero-fills a freshly allocated struct, since native allocations start with garbage. */
internal inline fun <reified T : kotlinx.cinterop.CStructVar> T.zeroed(): T {
    memset(this.ptr, 0, sizeOf<T>().convert())
    return this
}

/**
 * One shader compiled for every backend: MSL source for Metal, SPIR-V for Vulkan and DXIL for
 * Direct3D 12, with the resource counts SDL needs. Produced by the `compileShaders` build task
 * from HLSL; create the backend's shader with [GpuDevice.createShader].
 */
public class CompiledShader(
    public val stage: ShaderStage,
    private val msl: String,
    private val spirvBase64: String,
    private val dxilBase64: String,
    public val samplers: Int,
    public val storageTextures: Int,
    public val storageBuffers: Int,
    public val uniformBuffers: Int,
) {
    /** Returns the code for [format], and the entry point name that code uses. */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    internal fun code(format: ShaderFormat): Pair<ByteArray, String> = when (format) {
        // SPIRV-Cross renames `main` because Metal reserves it.
        ShaderFormat.MSL -> msl.encodeToByteArray() to "main0"
        ShaderFormat.SPIRV -> kotlin.io.encoding.Base64.decode(spirvBase64) to "main"
        ShaderFormat.DXIL -> kotlin.io.encoding.Base64.decode(dxilBase64) to "main"
    }

    override fun toString(): String =
        "CompiledShader(stage=$stage, samplers=$samplers, storageBuffers=$storageBuffers, uniformBuffers=$uniformBuffers)"
}

/**
 * Creates the backend's shader from [shader], picking the format this device supports.
 *
 * @throws SdlException if the backend rejects the shader.
 */
public fun GpuDevice.createShader(shader: CompiledShader): GpuShader {
    val format = shaderFormats.first()
    val (code, entryPoint) = shader.code(format)
    return createShader(
        code = code,
        format = format,
        stage = shader.stage,
        entryPoint = entryPoint,
        samplers = shader.samplers,
        storageTextures = shader.storageTextures,
        storageBuffers = shader.storageBuffers,
        uniformBuffers = shader.uniformBuffers,
    )
}
