package klyph.platform

import klyph.KlyphException
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memcpy
import stb.stbi_failure_reason
import stb.stbi_image_free
import stb.stbi_load_from_memory
import stb.stbi_write_png_to_func
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * A [width] by [height] grid of RGBA pixels with 8 bits per channel, stored row by row from the
 * top-left corner. Every pixel starts as transparent black.
 *
 * Read and write pixels with [get] and [set], or decode a PNG or JPEG file with [Image.decode].
 * [fill], [fillRect], [fillCircle] and [Image.drawLine] paint shapes and [draw] lays another
 * image, or a rectangle of one, over this one, all on the CPU and clipped to the image. A game
 * turns an image into a texture it can draw with `klyph.app.Assets.createTexture`.
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
    public operator fun get(x: Int, y: Int): Color = colorAt(pixels, offsetOf(x, y))

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
        fillRect(x = 0, y = 0, width = width, height = height, color = color)
    }

    /**
     * Sets every pixel of the rectangle [width] pixels wide and [height] pixels tall whose top-left
     * corner is at column [x] and row [y] to [color], alpha included, as [set] does. Nothing is
     * blended, so a transparent [color] clears the rectangle; [draw] is the call that blends.
     *
     * Coordinates are in pixels from the image's top-left corner. The parts of the rectangle
     * outside the image are left out, so a rectangle that lies wholly outside, or has a [width] or
     * [height] of 0, paints nothing. Pass [width], [height] and [color] by name; [x] and [y] are a
     * conventional pair.
     *
     * @throws IllegalArgumentException if [width] or [height] is negative.
     *
     * @sample samples.platform.Images.fillRect
     */
    public fun fillRect(x: Int, y: Int, width: Int, height: Int, color: Color) {
        require(width >= 0) { "Rectangle width $width is negative." }
        require(height >= 0) { "Rectangle height $height is negative." }
        val left = maxOf(x, 0)
        val top = maxOf(y, 0)
        val right = minOf(x.toLong() + width, this.width.toLong()).toInt()
        val bottom = minOf(y.toLong() + height, this.height.toLong()).toInt()
        if (left >= right || top >= bottom) return
        val rgba = color.rgba
        val red = (rgba shr 24).toByte()
        val green = (rgba shr 16).toByte()
        val blue = (rgba shr 8).toByte()
        val alpha = rgba.toByte()
        for (row in top until bottom) {
            val start = (row * this.width + left) * BYTES_PER_PIXEL
            val end = (row * this.width + right) * BYTES_PER_PIXEL
            for (i in start until end step BYTES_PER_PIXEL) {
                pixels[i] = red
                pixels[i + 1] = green
                pixels[i + 2] = blue
                pixels[i + 3] = alpha
            }
        }
    }

    /**
     * Draws [image] over this image with its top-left corner at column [x] and row [y], blending
     * each of its pixels by its alpha.
     *
     * A fully opaque pixel of [image] replaces the pixel under it and a fully transparent one
     * leaves it as it is. Any other is blended over it with the "over" rule for colors whose alpha
     * is not premultiplied, as the rim of [fillCircle] is: the result's alpha counts both pixels,
     * and a pixel drawn over a transparent one keeps its own color and alpha. The parts of [image]
     * outside this image are left out. [image] is never changed, and may be this image, which is
     * then read as it was before the call.
     *
     * @sample samples.platform.Images.draw
     */
    public fun draw(image: Image, x: Int, y: Int) {
        draw(image, x, y, sourceX = 0, sourceY = 0, sourceWidth = image.width, sourceHeight = image.height)
    }

    /**
     * Draws the rectangle of [image] [sourceWidth] pixels wide and [sourceHeight] pixels tall whose
     * top-left corner is at column [sourceX] and row [sourceY] over this image, with that corner at
     * column [x] and row [y], blending each of its pixels by its alpha as the [draw] without a
     * source rectangle does. This is how a sprite is cut out of a sheet image.
     *
     * Only the pixels inside the source rectangle are read, and the parts of it that fall outside
     * this image are left out, so a source rectangle with a [sourceWidth] or [sourceHeight] of 0
     * paints nothing. [image] is never changed, and may be this image, which is then read as it
     * was before the call. Pass the source arguments by name; [x] and [y] are a conventional pair.
     *
     * @throws IllegalArgumentException if [sourceWidth] or [sourceHeight] is negative, or the
     *   source rectangle does not lie wholly inside [image].
     *
     * @sample samples.platform.Images.draw
     */
    public fun draw(image: Image, x: Int, y: Int, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int) {
        require(sourceWidth >= 0) { "Source rectangle width $sourceWidth is negative." }
        require(sourceHeight >= 0) { "Source rectangle height $sourceHeight is negative." }
        require(
            sourceX >= 0 && sourceY >= 0 &&
                sourceX.toLong() + sourceWidth <= image.width && sourceY.toLong() + sourceHeight <= image.height,
        ) {
            "Source rectangle ${sourceWidth}x$sourceHeight at ($sourceX, $sourceY) is outside image size ${image.width}x${image.height}."
        }
        val left = maxOf(x, 0)
        val top = maxOf(y, 0)
        val right = minOf(x.toLong() + sourceWidth, width.toLong()).toInt()
        val bottom = minOf(y.toLong() + sourceHeight, height.toLong()).toInt()
        if (left >= right || top >= bottom) return
        // The source pixel under target pixel (column, row) is (column + shiftX, row + shiftY).
        val shiftX = sourceX - x
        val shiftY = sourceY - y
        // Reading a copy makes drawing an image into itself independent of the order the rows are visited in.
        // Only the source rows that are read are copied.
        val base = if (image === this) (top + shiftY) * image.width * BYTES_PER_PIXEL else 0
        val source = if (image === this) pixels.copyOfRange(base, (bottom + shiftY) * image.width * BYTES_PER_PIXEL) else image.pixels
        for (row in top until bottom) {
            val sourceStart = ((row + shiftY) * image.width + left + shiftX) * BYTES_PER_PIXEL
            val start = (row * width + left) * BYTES_PER_PIXEL
            for (offset in 0 until (right - left) * BYTES_PER_PIXEL step BYTES_PER_PIXEL) {
                val from = sourceStart + offset - base
                val to = start + offset
                val alpha = source[from + 3].toInt() and 0xFF
                if (alpha == 0) continue
                if (alpha == 0xFF) {
                    pixels[to] = source[from]
                    pixels[to + 1] = source[from + 1]
                    pixels[to + 2] = source[from + 2]
                    pixels[to + 3] = source[from + 3]
                } else {
                    blendOver(to, colorAt(source, from), weight = alpha / 255f)
                }
            }
        }
    }

    /**
     * Paints a disc of [radius] pixels centered on ([centerX], [centerY]) in [color], over what the
     * image already holds.
     *
     * Coordinates are in pixels from the image's top-left corner, where pixel (x, y) covers the
     * square from x to x + 1, so the center of a 16 by 16 image is (8, 8). Each pixel is covered by
     * how far its center lies inside the circle's edge, from 0 at half a pixel outside to 1 at half a
     * pixel inside, which gives the disc a soft edge one pixel wide. A fully covered pixel takes
     * [color]; a partly covered one is blended over its old color by [color]'s alpha times its
     * coverage, so a disc painted on a transparent image keeps its color all the way to the edge.
     * The parts of the disc outside the image are left out, and a radius of 0 paints nothing.
     * Pass the `Float` parameters by name.
     *
     * @throws IllegalArgumentException if [radius] is negative or not finite, or [centerX] or
     *   [centerY] is not finite.
     *
     * @sample samples.platform.Images.fillCircle
     */
    public fun fillCircle(centerX: Float, centerY: Float, radius: Float, color: Color) {
        require(centerX.isFinite() && centerY.isFinite()) { "Circle center ($centerX, $centerY) is not finite." }
        require(radius >= 0f && radius.isFinite()) { "Circle radius $radius is not a finite number of at least zero." }
        if (radius == 0f) return
        val reach = radius + 0.5f
        val left = floor(centerX - reach).toInt().coerceAtLeast(0)
        val right = ceil(centerX + reach).toInt().coerceAtMost(width)
        val top = floor(centerY - reach).toInt().coerceAtLeast(0)
        val bottom = ceil(centerY + reach).toInt().coerceAtMost(height)
        val sourceAlpha = color.alpha
        for (y in top until bottom) {
            val dy = y + 0.5f - centerY
            for (x in left until right) {
                val dx = x + 0.5f - centerX
                val coverage = (radius - sqrt(dx * dx + dy * dy) + 0.5f).coerceIn(0f, 1f)
                if (coverage == 0f) continue
                if (coverage == 1f) {
                    set(x, y, color)
                } else {
                    blendOver(offsetOf(x, y), color, weight = sourceAlpha * coverage)
                }
            }
        }
    }

    /**
     * Blends [color] at [weight] over the pixel at byte [i] with the "over" rule for colors whose
     * alpha is not premultiplied, so a transparent pixel takes [color]'s channels unchanged.
     */
    private fun blendOver(i: Int, color: Color, weight: Float) {
        val destinationAlpha = (pixels[i + 3].toInt() and 0xFF) / 255f
        val alpha = weight + destinationAlpha * (1f - weight)
        if (alpha <= 0f) return
        val keep = destinationAlpha * (1f - weight)
        pixels[i] = mixChannel(pixels[i], color.red, weight = weight, keep = keep, alpha = alpha)
        pixels[i + 1] = mixChannel(pixels[i + 1], color.green, weight = weight, keep = keep, alpha = alpha)
        pixels[i + 2] = mixChannel(pixels[i + 2], color.blue, weight = weight, keep = keep, alpha = alpha)
        pixels[i + 3] = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
    }

    private fun offsetOf(x: Int, y: Int): Int {
        if (x !in 0..<width || y !in 0..<height) {
            throw IndexOutOfBoundsException("Pixel ($x, $y) is out of bounds for image size ${width}x$height.")
        }
        return (y * width + x) * BYTES_PER_PIXEL
    }

    /** Returns the size as `Image(<width>x<height>)`, for example `Image(64x64)`. */
    override fun toString(): String = "Image(${width}x$height)"

    /** Holds the factories that decode image files. */
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

/** Returns the color of the pixel whose RGBA bytes start at byte [i] of [bytes]. */
private fun colorAt(bytes: ByteArray, i: Int): Color = Color(
    (bytes[i].toUByte().toUInt() shl 24) or
        (bytes[i + 1].toUByte().toUInt() shl 16) or
        (bytes[i + 2].toUByte().toUInt() shl 8) or
        bytes[i + 3].toUByte().toUInt(),
)

/**
 * Returns one color channel of an `Image.blendOver` result: the [destination] byte kept by [keep] plus
 * [source], from 0 to 1, by [weight], divided by the blended [alpha] and rounded back to a byte.
 * It is a plain function rather than a local one so the rim of a disc allocates nothing.
 */
private fun mixChannel(destination: Byte, source: Float, weight: Float, keep: Float, alpha: Float): Byte {
    val old = (destination.toInt() and 0xFF) / 255f
    return (((source * weight + old * keep) / alpha) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
}

/**
 * Sets the pixels of the straight line from column [x1] and row [y1] to column [x2] and row
 * [y2], both ends included, to [color], alpha included, as [Image.set] does. Nothing is blended, so a
 * transparent [color] clears the line.
 *
 * The line is one pixel wide with no antialiasing: it sets one pixel in each column, or in each
 * row when it is steeper than 45 degrees, the one nearest to the exact line, with a pixel halfway
 * between two always taking the same one, so the same pixels are set whichever end comes first.
 * The parts of the line outside the image are left out, and the time a line takes grows with the
 * image's size, not the line's length, so ends far outside the image cost nothing. Pass [color] by
 * name; ([x1], [y1]) and ([x2], [y2]) are conventional pairs.
 *
 * @sample samples.platform.Images.drawLine
 */
public fun Image.drawLine(x1: Int, y1: Int, x2: Int, y2: Int, color: Color) {
    val isSteep = abs(y2.toLong() - y1) > abs(x2.toLong() - x1)
    when {
        !isSteep && x1 <= x2 -> drawShallowLine(x1, y1, x2, y2, color, isTransposed = false)
        !isSteep -> drawShallowLine(x2, y2, x1, y1, color, isTransposed = false)
        y1 <= y2 -> drawShallowLine(y1, x1, y2, x2, color, isTransposed = true)
        else -> drawShallowLine(y2, x2, y1, x1, color, isTransposed = true)
    }
}

/**
 * Sets the pixels of the line from ([u1], [v1]) to ([u2], [v2]) to [color], where [u1] is at most
 * [u2] and the line rises or falls by at most as much as it runs: one pixel for each `u`, at the
 * `v` nearest to the line, a half rounded away from [v1]. `u` counts columns and `v` rows, or the
 * other way round when [isTransposed]. Only the `u` inside the image are visited.
 */
private fun Image.drawShallowLine(u1: Int, v1: Int, u2: Int, v2: Int, color: Color, isTransposed: Boolean) {
    val uSize = if (isTransposed) height else width
    val vSize = if (isTransposed) width else height
    val run = u2.toLong() - u1
    val rise = abs(v2.toLong() - v1)
    val direction = if (v2 >= v1) 1L else -1L
    val first = maxOf(0L, -u1.toLong())
    val last = minOf(run, uSize - 1L - u1)
    if (first > last) return
    if (run == 0L) {
        if (v1 in 0 until vSize) set(if (isTransposed) v1 else u1, if (isTransposed) u1 else v1, color)
        return
    }
    // At step i the line has risen i * rise / run pixels, held as whole + remainder / run. Both
    // factors are below 2^32, so their product fits in a ULong.
    val product = first.toULong() * rise.toULong()
    var whole = (product / run.toULong()).toLong()
    var remainder = (product % run.toULong()).toLong()
    for (step in first..last) {
        val rounded = if (2 * remainder >= run) whole + 1 else whole
        val v = v1 + direction * rounded
        if (v in 0L until vSize) {
            val u = (u1 + step).toInt()
            if (isTransposed) set(v.toInt(), u, color) else set(u, v.toInt(), color)
        }
        remainder += rise
        if (remainder >= run) {
            remainder -= run
            whole++
        }
    }
}

/**
 * Writes this image to the file at [path] as a PNG file with four 8-bit channels, creating the
 * file or replacing its contents. [Image.decode] reads it back pixel for pixel.
 *
 * A relative [path] resolves against [Files.basePath]; an absolute one is used as given. A
 * missing directory on the way to the file is created first, as [Files.writeBytes] does.
 *
 * Has no sample because it writes a file to disk.
 *
 * @throws FileException if the file cannot be created or written, for example because a part of
 *   its path is a file rather than a directory.
 * @throws ImageException if the encoder runs out of memory.
 */
public fun Image.save(path: String) {
    Files.writeBytes(path, encodePng())
}

/**
 * Returns this image encoded as a PNG file.
 *
 * @throws ImageException if the encoder runs out of memory.
 */
internal fun Image.encodePng(): ByteArray {
    val sink = PngSink()
    val ref = StableRef.create(sink)
    try {
        val written = pixels.usePinned { pinned ->
            stbi_write_png_to_func(
                staticCFunction { context: COpaquePointer?, data: COpaquePointer?, size: Int ->
                    context!!.asStableRef<PngSink>().get().append(data!!, size)
                },
                ref.asCPointer(),
                width,
                height,
                4,
                pinned.addressOf(0),
                width * 4,
            )
        }
        if (written == 0) throw ImageException("Image ${width}x$height could not be encoded as PNG.")
    } finally {
        ref.dispose()
    }
    return sink.toByteArray()
}

/** Collects the bytes stb_image_write hands to its write callback; it may call it more than once. */
private class PngSink {
    private var bytes = ByteArray(0)
    private var size = 0

    fun append(data: COpaquePointer, count: Int) {
        if (count <= 0) return
        if (size + count > bytes.size) bytes = bytes.copyOf(maxOf(size + count, bytes.size * 2))
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(size), data, count.convert()) }
        size += count
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    override fun toString(): String = "PngSink(size=$size)"
}

/**
 * Thrown when image data cannot be decoded or encoded. The message gives the decoder's reason, or
 * names the file when a loader wraps an earlier [ImageException] as [cause].
 */
public class ImageException(message: String, cause: Throwable? = null) : KlyphException(message, cause)
