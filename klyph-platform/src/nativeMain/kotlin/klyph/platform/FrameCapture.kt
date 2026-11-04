package klyph.platform

import klyph.KlyphException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import sdl3.SDL_CreateGPUTransferBuffer
import sdl3.SDL_DownloadFromGPUTexture
import sdl3.SDL_GPUTextureFormat
import sdl3.SDL_GPUTextureRegion
import sdl3.SDL_GPUTextureTransferInfo
import sdl3.SDL_GPUTransferBufferCreateInfo
import sdl3.SDL_GPUTransferBufferUsage
import sdl3.SDL_MapGPUTransferBuffer
import sdl3.SDL_ReleaseGPUTransferBuffer
import sdl3.SDL_UnmapGPUTransferBuffer

/**
 * A frame read back from the GPU for the captures queued with [Window.capture]. Recorded by
 * [CommandBuffer.recordCaptureOrNull] into the frame's command buffer; once that buffer has been
 * submitted and its fence waited on, [save] writes the PNG files and frees the read-back memory.
 */
@KlyphPlatformApi
public class FrameCapture internal constructor(
    private val device: GpuDevice,
    private val buffer: TransferBuffer,
    private val width: Int,
    private val height: Int,
    private val format: TextureFormat,
    private val paths: List<String>,
) {
    private var isSaved = false

    /**
     * Writes the captured frame to every path queued for it, as a PNG file with every pixel
     * opaque, and frees the read-back memory. Call it once, after the command buffer the capture
     * was recorded into has been submitted with [CommandBuffer.submitAndAcquireFence] and
     * [GpuDevice.waitFor] has returned for that fence; earlier, the memory does not hold the
     * frame yet.
     *
     * Failures do not throw: a path that cannot be written, or a read-back that cannot be mapped,
     * prints one line to standard error through [printError], and the other paths are still
     * written.
     *
     * @throws IllegalStateException if the capture has already been saved.
     */
    public fun save() {
        check(!isSaved) { "Frame capture $this has already been saved." }
        isSaved = true
        try {
            val image = readImage()
            val png = image.encodePng()
            for (path in paths) {
                try {
                    Files.writeBytes(path, png)
                } catch (e: FileException) {
                    printError("klyph: frame capture failed: ${e.message}")
                }
            }
        } catch (e: KlyphException) {
            printError("klyph: frame capture to ${paths.joinToString()} failed: ${e.message}")
        } finally {
            SDL_ReleaseGPUTransferBuffer(device.handle, buffer.handle)
        }
    }

    /** Copies the read-back texels into a new image, turning BGRA into RGBA and making every pixel opaque. */
    private fun readImage(): Image {
        val image = Image(width, height)
        val pixels = image.pixels
        val mapped = SDL_MapGPUTransferBuffer(device.handle, buffer.handle, false)
            ?: sdlFailure("SDL_MapGPUTransferBuffer")
        try {
            pixels.usePinned { pinned -> memcpy(pinned.addressOf(0), mapped, pixels.size.convert()) }
        } finally {
            SDL_UnmapGPUTransferBuffer(device.handle, buffer.handle)
        }
        val swapRedBlue = format.isBgra8()
        for (i in pixels.indices step 4) {
            if (swapRedBlue) {
                val blue = pixels[i]
                pixels[i] = pixels[i + 2]
                pixels[i + 2] = blue
            }
            // The display ignores alpha, so the file shows what was presented.
            pixels[i + 3] = -1
        }
        return image
    }

    override fun toString(): String = "FrameCapture(${width}x$height, format=$format, paths=$paths)"
}

/**
 * Records a read-back of [texture] into this command buffer if [window] has captures queued with
 * [Window.capture], and returns it, or returns `null` and records nothing when none is queued.
 * Takes the queued captures off [window] either way.
 *
 * Call it after the frame's last render pass into [texture] has ended and before the buffer is
 * submitted; then submit with [CommandBuffer.submitAndAcquireFence], wait for the fence and call
 * [FrameCapture.save]. [texture] is [width] by [height] pixels in [format], which must be an 8-bit
 * RGBA or BGRA format such as the swapchain's or [TextureFormat.RGBA8]. When [texture] is the
 * swapchain texture last acquired for [window], the size SDL reported when it was acquired is read
 * back instead of [width] by [height], since the window may have changed size since. With no capture queued the call only checks the queue, so it costs nothing to make
 * every frame.
 *
 * A capture that cannot be recorded, because [format] is not supported or the read-back memory
 * cannot be allocated or copied, prints one line to standard error through [printError] and is dropped, and
 * the function returns `null`.
 */
@KlyphPlatformApi
public fun CommandBuffer.recordCaptureOrNull(
    device: GpuDevice,
    window: Window,
    texture: GpuTexture,
    width: Int,
    height: Int,
    format: TextureFormat,
): FrameCapture? {
    val paths = window.takeQueuedCapturesOrNull() ?: return null
    val isSwapchain = texture.handle == window.swapchainTexture
    val captureWidth = if (isSwapchain) window.swapchainWidth else width
    val captureHeight = if (isSwapchain) window.swapchainHeight else height
    if (!format.isBgra8() && !format.isRgba8()) {
        printError("klyph: frame capture to ${paths.joinToString()} failed: $format is not an 8-bit RGBA or BGRA format.")
        return null
    }
    if (captureWidth <= 0 || captureHeight <= 0) {
        printError("klyph: frame capture to ${paths.joinToString()} failed: the frame is ${captureWidth}x$captureHeight pixels.")
        return null
    }
    return memScoped {
        val info = alloc<SDL_GPUTransferBufferCreateInfo>().zeroed()
        info.usage = SDL_GPUTransferBufferUsage.SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD
        info.size = (captureWidth.toLong() * captureHeight * 4).toUInt()
        val buffer = SDL_CreateGPUTransferBuffer(device.handle, info.ptr)
        if (buffer == null) {
            printError("klyph: frame capture to ${paths.joinToString()} failed: ${sdlFailureMessage("SDL_CreateGPUTransferBuffer")}")
            return@memScoped null
        }
        val copy = try {
            beginCopyPass()
        } catch (e: SdlException) {
            SDL_ReleaseGPUTransferBuffer(device.handle, buffer)
            printError("klyph: frame capture to ${paths.joinToString()} failed: ${e.message}")
            return@memScoped null
        }
        val region = alloc<SDL_GPUTextureRegion>().zeroed()
        region.texture = texture.handle
        region.w = captureWidth.toUInt()
        region.h = captureHeight.toUInt()
        region.d = 1u
        val destination = alloc<SDL_GPUTextureTransferInfo>().zeroed()
        destination.transfer_buffer = buffer
        destination.pixels_per_row = captureWidth.toUInt()
        destination.rows_per_layer = captureHeight.toUInt()
        SDL_DownloadFromGPUTexture(copy.handle, region.ptr, destination.ptr)
        copy.end()
        FrameCapture(device, TransferBuffer(buffer), captureWidth, captureHeight, format, paths)
    }
}

private fun TextureFormat.isBgra8(): Boolean =
    raw == SDL_GPUTextureFormat.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM ||
        raw == SDL_GPUTextureFormat.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB

private fun TextureFormat.isRgba8(): Boolean =
    raw == SDL_GPUTextureFormat.SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM ||
        raw == SDL_GPUTextureFormat.SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB
