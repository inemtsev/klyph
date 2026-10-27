package klyph.platform

import cnames.structs.SDL_GPUCommandBuffer
import cnames.structs.SDL_GPUDevice
import cnames.structs.SDL_GPUFence
import kotlin.jvm.JvmInline
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import sdl3.SDL_AcquireGPUCommandBuffer
import sdl3.SDL_ClaimWindowForGPUDevice
import sdl3.SDL_CreateGPUDevice
import sdl3.SDL_DestroyGPUDevice
import sdl3.SDL_GPUPresentMode
import sdl3.SDL_GPUSwapchainComposition
import sdl3.SDL_GPUTextureFormat
import sdl3.SDL_GPU_SHADERFORMAT_DXIL
import sdl3.SDL_GPU_SHADERFORMAT_MSL
import sdl3.SDL_GPU_SHADERFORMAT_SPIRV
import sdl3.SDL_GetGPUDeviceDriver
import sdl3.SDL_GetGPUShaderFormats
import sdl3.SDL_GetGPUSwapchainTextureFormat
import sdl3.SDL_ReleaseGPUFence
import sdl3.SDL_ReleaseWindowFromGPUDevice
import sdl3.SDL_SubmitGPUCommandBufferAndAcquireFence
import sdl3.SDL_WaitForGPUFences
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import sdl3.SDL_SetGPUSwapchainParameters
import sdl3.SDL_SubmitGPUCommandBuffer
import sdl3.SDL_WindowSupportsGPUPresentMode

/**
 * Compiled shader formats a [GpuDevice] can consume. Which one is used depends on the backend
 * SDL picks: SPIR-V for Vulkan, DXIL for Direct3D 12, MSL for Metal.
 */
public enum class ShaderFormat(internal val bit: UInt) {
    SPIRV(SDL_GPU_SHADERFORMAT_SPIRV),
    DXIL(SDL_GPU_SHADERFORMAT_DXIL),
    MSL(SDL_GPU_SHADERFORMAT_MSL),
}

/**
 * Whether the GPU backend runs its validation layer. Validation reports API misuse at a large
 * performance cost, so enable it in development builds only.
 */
public enum class GpuValidation {
    ENABLED,
    DISABLED,
}

/**
 * How frames are handed to the display.
 */
public enum class PresentMode(internal val raw: SDL_GPUPresentMode) {
    /** Waits for the display's refresh. Never tears, caps the frame rate. */
    VSYNC(SDL_GPUPresentMode.SDL_GPU_PRESENTMODE_VSYNC),

    /** Presents as soon as a frame is ready. May tear. Use for benchmarks. */
    IMMEDIATE(SDL_GPUPresentMode.SDL_GPU_PRESENTMODE_IMMEDIATE),

    /** Keeps the newest frame and presents it at the next refresh. Not supported everywhere. */
    MAILBOX(SDL_GPUPresentMode.SDL_GPU_PRESENTMODE_MAILBOX),
}

/**
 * Pixel format of a GPU texture, as chosen by SDL for a swapchain or requested for a created texture.
 */
@JvmInline
public value class TextureFormat internal constructor(internal val raw: SDL_GPUTextureFormat) {
    override fun toString(): String = "TextureFormat(${raw.name})"

    public companion object {
        /** Four 8-bit channels, red first, not sRGB-encoded. */
        public val RGBA8: TextureFormat = TextureFormat(SDL_GPUTextureFormat.SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM)
    }
}

/**
 * A GPU device. Create one with the [GpuDevice] factory, [claim] a window to present into, and
 * [close] it after releasing every window and GPU resource created from it.
 *
 * Samples live in the sandbox because they need a display.
 */
public class GpuDevice internal constructor(internal val handle: CPointer<SDL_GPUDevice>) : AutoCloseable {
    /** Name of the backend SDL chose, such as `metal`, `vulkan` or `direct3d12`. */
    public val driverName: String
        get() = SDL_GetGPUDeviceDriver(handle)?.toKString() ?: "unknown"

    /** Shader formats the chosen backend accepts; on every current backend exactly one. */
    public val shaderFormats: Set<ShaderFormat>
        get() {
            val bits = SDL_GetGPUShaderFormats(handle)
            return ShaderFormat.entries.filterTo(LinkedHashSet()) { bits and it.bit != 0u }
        }

    /**
     * Creates a swapchain for [window] so this device can present into it.
     *
     * @throws SdlException if the window cannot be claimed.
     */
    public fun claim(window: Window) {
        if (!SDL_ClaimWindowForGPUDevice(handle, window.handle)) sdlFailure("SDL_ClaimWindowForGPUDevice")
    }

    /** Destroys the swapchain of a [window] claimed earlier. */
    public fun release(window: Window) {
        SDL_ReleaseWindowFromGPUDevice(handle, window.handle)
    }

    /**
     * Changes how frames are presented into a claimed [window].
     *
     * @throws SdlException if the mode is not supported for this window.
     */
    public fun setPresentMode(window: Window, mode: PresentMode) {
        val composition = SDL_GPUSwapchainComposition.SDL_GPU_SWAPCHAINCOMPOSITION_SDR
        if (!SDL_SetGPUSwapchainParameters(handle, window.handle, composition, mode.raw)) {
            sdlFailure("SDL_SetGPUSwapchainParameters")
        }
    }

    /** Returns `true` if a claimed [window] can present with [mode]. */
    public fun supportsPresentMode(window: Window, mode: PresentMode): Boolean =
        SDL_WindowSupportsGPUPresentMode(handle, window.handle, mode.raw)

    /** Returns the pixel format of the swapchain of a claimed [window]. Pipelines that draw into it must match. */
    public fun swapchainFormat(window: Window): TextureFormat =
        TextureFormat(SDL_GetGPUSwapchainTextureFormat(handle, window.handle))

    /**
     * Returns a command buffer to record one frame's work into. Every acquired buffer must be
     * submitted with [CommandBuffer.submit].
     *
     * @throws SdlException if no command buffer is available.
     */
    public fun acquireCommandBuffer(): CommandBuffer =
        CommandBuffer(SDL_AcquireGPUCommandBuffer(handle) ?: sdlFailure("SDL_AcquireGPUCommandBuffer"))

    override fun close() {
        SDL_DestroyGPUDevice(handle)
    }

    override fun toString(): String = "GpuDevice(driver=$driverName)"
}

/**
 * Creates a GPU device that accepts shaders in any of [shaderFormats]. Pass every format you ship
 * so SDL can pick a backend on each platform.
 *
 * @throws IllegalArgumentException if [shaderFormats] is empty.
 * @throws SdlException if no backend supports the formats.
 */
public fun GpuDevice(shaderFormats: Set<ShaderFormat>, validation: GpuValidation = GpuValidation.DISABLED): GpuDevice {
    require(shaderFormats.isNotEmpty()) { "Shader format set is empty; at least one format is required." }
    var bits = 0u
    for (format in shaderFormats) bits = bits or format.bit
    val handle = SDL_CreateGPUDevice(bits, validation == GpuValidation.ENABLED, null)
        ?: sdlFailure("SDL_CreateGPUDevice")
    return GpuDevice(handle)
}

/**
 * One frame's recorded GPU work. Acquire with [GpuDevice.acquireCommandBuffer] and finish with [submit].
 */
@JvmInline
public value class CommandBuffer internal constructor(internal val handle: CPointer<SDL_GPUCommandBuffer>) {
    /**
     * Sends the recorded work to the GPU. The buffer must not be used afterwards.
     *
     * @throws SdlException if submission fails.
     */
    public fun submit() {
        if (!SDL_SubmitGPUCommandBuffer(handle)) sdlFailure("SDL_SubmitGPUCommandBuffer")
    }

    /**
     * Sends the recorded work to the GPU and returns a fence that signals when it has finished.
     * Release the fence with [GpuDevice.release] after waiting on it.
     *
     * @throws SdlException if submission fails.
     */
    public fun submitAndAcquireFence(): GpuFence =
        GpuFence(SDL_SubmitGPUCommandBufferAndAcquireFence(handle) ?: sdlFailure("SDL_SubmitGPUCommandBufferAndAcquireFence"))

    override fun toString(): String = "CommandBuffer"
}

/**
 * Signals when submitted GPU work completes. Obtain from [CommandBuffer.submitAndAcquireFence].
 */
@JvmInline
public value class GpuFence internal constructor(internal val handle: CPointer<SDL_GPUFence>) {
    override fun toString(): String = "GpuFence"
}

/**
 * Blocks until the work behind [fence] has finished on the GPU.
 *
 * @throws SdlException if waiting fails.
 */
public fun GpuDevice.waitFor(fence: GpuFence): Unit = memScoped {
    val fences = allocPointerTo<SDL_GPUFence>()
    fences.value = fence.handle
    if (!SDL_WaitForGPUFences(handle, true, fences.ptr, 1u)) sdlFailure("SDL_WaitForGPUFences")
}

/** Frees [fence]. */
public fun GpuDevice.release(fence: GpuFence) {
    SDL_ReleaseGPUFence(handle, fence.handle)
}
