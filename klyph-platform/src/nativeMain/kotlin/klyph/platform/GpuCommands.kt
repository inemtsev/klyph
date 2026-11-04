package klyph.platform

import cnames.structs.SDL_GPUBuffer
import cnames.structs.SDL_GPUCopyPass
import cnames.structs.SDL_GPURenderPass
import cnames.structs.SDL_GPUTexture
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import sdl3.SDL_BeginGPUCopyPass
import sdl3.SDL_BeginGPURenderPass
import sdl3.SDL_BindGPUFragmentSamplers
import sdl3.SDL_BindGPUGraphicsPipeline
import sdl3.SDL_BindGPUVertexStorageBuffers
import sdl3.SDL_DrawGPUPrimitives
import sdl3.SDL_EndGPUCopyPass
import sdl3.SDL_EndGPURenderPass
import sdl3.SDL_GPUBufferRegion
import sdl3.SDL_GPUColorTargetInfo
import sdl3.SDL_GPULoadOp
import sdl3.SDL_GPUStoreOp
import sdl3.SDL_GPUTextureRegion
import sdl3.SDL_GPUTextureSamplerBinding
import sdl3.SDL_GPUTextureTransferInfo
import sdl3.SDL_GPUTransferBufferLocation
import sdl3.SDL_PushGPUVertexUniformData
import sdl3.SDL_UploadToGPUBuffer
import sdl3.SDL_UploadToGPUTexture
import sdl3.SDL_WaitAndAcquireGPUSwapchainTexture

/**
 * A copy pass that is being recorded. Uploads go here, before any render pass of the frame.
 */
@KlyphPlatformApi
public value class CopyPass internal constructor(internal val handle: CPointer<SDL_GPUCopyPass>) {
    /** Finishes the pass. */
    public fun end() {
        SDL_EndGPUCopyPass(handle)
    }

    override fun toString(): String = "CopyPass"
}

/**
 * A render pass that is being recorded. Call [end] before recording anything else.
 */
@KlyphPlatformApi
public value class RenderPass internal constructor(internal val handle: CPointer<SDL_GPURenderPass>) {
    /** Finishes the pass. */
    public fun end() {
        SDL_EndGPURenderPass(handle)
    }

    override fun toString(): String = "RenderPass"
}

/**
 * Waits for the next swapchain texture of [window] and returns it, or `null` when the window
 * cannot be drawn to right now, such as while it is minimized. The texture is valid until this
 * command buffer is submitted.
 *
 * @throws SdlException if the swapchain cannot be acquired.
 */
@KlyphPlatformApi
public fun CommandBuffer.acquireSwapchainTexture(window: Window): GpuTexture? = memScoped {
    val texture = allocPointerTo<SDL_GPUTexture>()
    val width = alloc<UIntVar>()
    val height = alloc<UIntVar>()
    if (!SDL_WaitAndAcquireGPUSwapchainTexture(handle, window.handle, texture.ptr, width.ptr, height.ptr)) {
        sdlFailure("SDL_WaitAndAcquireGPUSwapchainTexture")
    }
    // Frame capture reads back no more than this, whatever size the caller believes the window has.
    window.swapchainTexture = texture.value
    window.swapchainWidth = width.value.toInt()
    window.swapchainHeight = height.value.toInt()
    texture.value?.let { GpuTexture(it) }
}

/**
 * Begins a copy pass for uploads.
 *
 * @throws SdlException if the pass cannot be started.
 */
@KlyphPlatformApi
public fun CommandBuffer.beginCopyPass(): CopyPass =
    CopyPass(SDL_BeginGPUCopyPass(handle) ?: sdlFailure("SDL_BeginGPUCopyPass"))

/**
 * Copies [sizeInBytes] bytes from [source] at [sourceOffset] into [destination] at [destinationOffset].
 * Pass the `Int` parameters by name.
 */
@KlyphPlatformApi
public fun CopyPass.uploadToBuffer(
    source: TransferBuffer,
    destination: GpuBuffer,
    sizeInBytes: Int,
    cycle: Cycle,
    sourceOffset: Int = 0,
    destinationOffset: Int = 0,
): Unit = memScoped {
    val location = alloc<SDL_GPUTransferBufferLocation>().zeroed()
    location.transfer_buffer = source.handle
    location.offset = sourceOffset.toUInt()
    val region = alloc<SDL_GPUBufferRegion>().zeroed()
    region.buffer = destination.handle
    region.offset = destinationOffset.toUInt()
    region.size = sizeInBytes.toUInt()
    SDL_UploadToGPUBuffer(handle, location.ptr, region.ptr, cycle == Cycle.CYCLE)
}

/**
 * Copies tightly packed texels for a whole [width] by [height] texture from [source] at
 * [sourceOffset] into [destination].
 */
@KlyphPlatformApi
public fun CopyPass.uploadToTexture(
    source: TransferBuffer,
    destination: GpuTexture,
    width: Int,
    height: Int,
    sourceOffset: Int = 0,
): Unit = memScoped {
    val transfer = alloc<SDL_GPUTextureTransferInfo>().zeroed()
    transfer.transfer_buffer = source.handle
    transfer.offset = sourceOffset.toUInt()
    transfer.pixels_per_row = width.toUInt()
    transfer.rows_per_layer = height.toUInt()
    val region = alloc<SDL_GPUTextureRegion>().zeroed()
    region.texture = destination.handle
    region.w = width.toUInt()
    region.h = height.toUInt()
    region.d = 1u
    SDL_UploadToGPUTexture(handle, transfer.ptr, region.ptr, false)
}

/**
 * Begins a render pass that clears [target] to [clearColor] and keeps whatever is drawn into it.
 *
 * @throws SdlException if the pass cannot be started.
 */
@KlyphPlatformApi
public fun CommandBuffer.beginRenderPass(target: GpuTexture, clearColor: Color): RenderPass = memScoped {
    val info = alloc<SDL_GPUColorTargetInfo>().zeroed()
    info.texture = target.handle
    info.clear_color.r = clearColor.red
    info.clear_color.g = clearColor.green
    info.clear_color.b = clearColor.blue
    info.clear_color.a = clearColor.alpha
    info.load_op = SDL_GPULoadOp.SDL_GPU_LOADOP_CLEAR
    info.store_op = SDL_GPUStoreOp.SDL_GPU_STOREOP_STORE
    RenderPass(SDL_BeginGPURenderPass(handle, info.ptr, 1u, null) ?: sdlFailure("SDL_BeginGPURenderPass"))
}

/**
 * Copies [floats] into vertex uniform [slot] for the draws that follow. Sizes are limited by the
 * backend; a 4x4 matrix is always fine.
 */
@KlyphPlatformApi
public fun CommandBuffer.pushVertexUniforms(slot: Int, floats: FloatArray) {
    floats.usePinned { pinned ->
        SDL_PushGPUVertexUniformData(handle, slot.toUInt(), pinned.addressOf(0), (floats.size * 4).convert())
    }
}

/** Makes [pipeline] current for the draws that follow. */
@KlyphPlatformApi
public fun RenderPass.bind(pipeline: GraphicsPipeline) {
    SDL_BindGPUGraphicsPipeline(handle, pipeline.handle)
}

/** Binds [buffer] as vertex storage buffer [slot]. */
@KlyphPlatformApi
public fun RenderPass.bindVertexStorageBuffer(slot: Int, buffer: GpuBuffer): Unit = memScoped {
    val slotBuffer = allocPointerTo<SDL_GPUBuffer>()
    slotBuffer.value = buffer.handle
    SDL_BindGPUVertexStorageBuffers(handle, slot.toUInt(), slotBuffer.ptr, 1u)
}

/** Binds [texture] with [sampler] as fragment sampler [slot]. */
@KlyphPlatformApi
public fun RenderPass.bindFragmentSampler(slot: Int, texture: GpuTexture, sampler: Sampler): Unit = memScoped {
    val binding = alloc<SDL_GPUTextureSamplerBinding>().zeroed()
    binding.texture = texture.handle
    binding.sampler = sampler.handle
    SDL_BindGPUFragmentSamplers(handle, slot.toUInt(), binding.ptr, 1u)
}

/** Draws [vertexCount] vertices starting at [firstVertex] with the bound pipeline. Pass the `Int` parameters by name. */
@KlyphPlatformApi
public fun RenderPass.draw(vertexCount: Int, firstVertex: Int = 0) {
    SDL_DrawGPUPrimitives(handle, vertexCount.toUInt(), 1u, firstVertex.toUInt(), 0u)
}
