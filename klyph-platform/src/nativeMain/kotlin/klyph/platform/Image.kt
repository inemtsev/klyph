package klyph.platform

import klyph.KlyphException
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memcpy
import stb.stbi_failure_reason
import stb.stbi_image_free
import stb.stbi_load_from_memory

/**
 * A [width] by [height] grid of RGBA pixels with 8 bits per channel, stored row by row from the
 * top-left corner. Every pixel starts as transparent black.
 *
 * Read and write pixels with [get] and [set], or decode a PNG or JPEG file with [Image.decode].
 * Upload an image to the GPU with [GpuDevice.createTexture].
 *
 * @throws IllegalArgumentException if [width] or [height] is not positive, or the image would
 *   need more than [Int.MAX_VALUE] bytes.
 */
public class Image(public val width: Int, public val height: Int) {
    init {
        require(width > 0) { "Image width $width is not positive." }
        require(height > 0) { "Image height $height is not positive." }
        require(width.toLong() * height * BYTES_PER_PIXEL <= Int.MAX_VALUE) {
            "Image size ${width}x$height needs more than ${Int.MAX_VALUE} bytes."
        }
    }

    /** RGBA8 bytes, row-major from the top-left corner, ready for a texture upload. */
    internal val pixels: ByteArray = ByteArray(width * height * BYTES_PER_PIXEL)

    /**
     * Returns the color of the pixel at column [x] and row [y], counted from the top-left corner.
     *
     * @throws IndexOutOfBoundsException if [x] is outside `0 until width` or [y] is outside
     *   `0 until height`.
     *
     * @sample samples.platform.Images.get
     */
    public operator fun get(x: Int, y: Int): Color {
        val i = offsetOf(x, y)
        return Color(
            (pixels[i].toUByte().toUInt() shl 24) or
                (pixels[i + 1].toUByte().toUInt() shl 16) or
                (pixels[i + 2].toUByte().toUInt() shl 8) or
                pixels[i + 3].toUByte().toUInt(),
        )
    }

    /**
     * Sets the pixel at column [x] and row [y], counted from the top-left corner, to [color].
     *
     * @throws IndexOutOfBoundsException if [x] is outside `0 until width` or [y] is outside
     *   `0 until height`.
     *
     * @sample samples.platform.Images.get
     */
    public operator fun set(x: Int, y: Int, color: Color) {
        val i = offsetOf(x, y)
        val rgba = color.rgba
        pixels[i] = (rgba shr 24).toByte()
        pixels[i + 1] = (rgba shr 16).toByte()
        pixels[i + 2] = (rgba shr 8).toByte()
        pixels[i + 3] = rgba.toByte()
    }

    /**
     * Sets every pixel to [color].
     *
     * @sample samples.platform.Images.fill
     */
    public fun fill(color: Color) {
        val rgba = color.rgba
        val red = (rgba shr 24).toByte()
        val green = (rgba shr 16).toByte()
        val blue = (rgba shr 8).toByte()
        val alpha = rgba.toByte()
        for (i in pixels.indices step BYTES_PER_PIXEL) {
            pixels[i] = red
            pixels[i + 1] = green
            pixels[i + 2] = blue
            pixels[i + 3] = alpha
        }
    }

    private fun offsetOf(x: Int, y: Int): Int {
        if (x !in 0..<width || y !in 0..<height) {
            throw IndexOutOfBoundsException("Pixel ($x, $y) is out of bounds for image size ${width}x$height.")
        }
        return (y * width + x) * BYTES_PER_PIXEL
    }

    /** Returns the size as `Image(<width>x<height>)`, for example `Image(64x64)`. */
    override fun toString(): String = "Image(${width}x$height)"

    public companion object {
        private const val BYTES_PER_PIXEL = 4

        /**
         * Decodes a PNG or JPEG file held in [bytes] into a new image. Images with fewer channels
         * are expanded to RGBA, with opaque alpha when the file has none.
         *
         * [bytes] is only read during the call and never retained, so the caller may reuse it.
         *
         * Has no sample because it needs a file on disk.
         *
         * @throws ImageException if [bytes] is empty, is not a PNG or JPEG file, or is corrupt.
         */
        public fun decode(bytes: ByteArray): Image = decodeOrElse(
            bytes,
            onSuccess = { it },
            onFailure = { reason -> throw ImageException("Image data could not be decoded: $reason.") },
        )

        /**
         * Decodes a PNG or JPEG file held in [bytes] into a new image, or returns `null` when
         * [bytes] is empty, is not a PNG or JPEG file, or is corrupt. Images with fewer channels
         * are expanded to RGBA, with opaque alpha when the file has none.
         *
         * [bytes] is only read during the call and never retained, so the caller may reuse it.
         *
         * @sample samples.platform.Images.decodeOrNull
         */
        public fun decodeOrNull(bytes: ByteArray): Image? = decodeOrElse(
            bytes,
            onSuccess = { it },
            onFailure = { null },
        )

        private inline fun <R> decodeOrElse(
            bytes: ByteArray,
            onSuccess: (Image) -> R,
            onFailure: (reason: String) -> R,
        ): R {
            if (bytes.isEmpty()) return onFailure("data is empty")
            val image = memScoped {
                val width = alloc<IntVar>()
                val height = alloc<IntVar>()
                val channels = alloc<IntVar>()
                val decoded = bytes.usePinned { pinned ->
                    stbi_load_from_memory(
                        pinned.addressOf(0).reinterpret(),
                        bytes.size,
                        width.ptr,
                        height.ptr,
                        channels.ptr,
                        BYTES_PER_PIXEL,
                    )
                } ?: return onFailure(stbi_failure_reason()?.toKString() ?: "unknown format")
                try {
                    val image = Image(width.value, height.value)
                    image.pixels.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), decoded, image.pixels.size.convert())
                    }
                    image
                } finally {
                    stbi_image_free(decoded)
                }
            }
            return onSuccess(image)
        }
    }
}

/**
 * Thrown when image data cannot be decoded. The message gives the decoder's reason, or names the
 * file when a loader wraps an earlier [ImageException] as [cause].
 */
public class ImageException(message: String, cause: Throwable? = null) : KlyphException(message, cause)
