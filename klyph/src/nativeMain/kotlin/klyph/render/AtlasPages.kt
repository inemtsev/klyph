package klyph.render

import klyph.platform.Color
import klyph.platform.GpuDevice
import klyph.platform.GpuTexture
import klyph.platform.Image
import klyph.platform.SamplerFilter
import klyph.platform.TextureUsage
import klyph.platform.beginRenderPass
import klyph.platform.copyFrom
import klyph.platform.createTexture
import klyph.platform.release

/**
 * The shared texture pages that `Assets.region` packs images into, so that sprites showing
 * different files draw from one texture, and in one draw call where they share a layer.
 *
 * Every page is [pageSize] texels square and has one [SamplerFilter]; [createPage] makes it empty
 * (transparent) when no page of the filter has room for an image. A page is never grown or
 * repacked, because the regions already handed out keep their texture coordinates. Each image is
 * placed by a [SkylinePacker] with a ring one texel wide around it that repeats its own edge
 * texels, corners included, so a sprite sampled with [SamplerFilter.LINEAR] at any scale, or with
 * [SamplerFilter.NEAREST] at a texel's edge, reads the image's own edge and never its neighbor.
 * [write] copies the image and its ring into the page as given. Pages are tried oldest first.
 *
 * Not thread-safe. [close] closes every page.
 */
internal class AtlasPages(
    val pageSize: Int,
    private val createPage: (size: Int, filter: SamplerFilter) -> Texture,
    private val write: (page: Texture, image: Image, x: Int, y: Int) -> Unit,
) : AutoCloseable {
    init {
        require(pageSize > 2 * RING) { "Atlas page size $pageSize is too small to hold one texel and its ring." }
    }

    private class Page(val texture: Texture, val packer: SkylinePacker)

    private val pages = ArrayList<Page>()

    /** The pages made so far, in creation order, of every filter. */
    val textures: List<Texture> get() = pages.map { it.texture }

    /** Number of pages made so far, of every filter. */
    val size: Int get() = pages.size

    /** Returns whether an image of [width] by [height] texels fits on a page together with its ring. */
    fun fits(width: Int, height: Int): Boolean = width <= pageSize - 2 * RING && height <= pageSize - 2 * RING

    /**
     * Packs [image] into a page sampled with [filter] and returns the region that covers it, or
     * returns `null` and makes nothing when the image and its ring are larger than a page. Opens
     * a new page when no page of [filter] has room.
     */
    fun add(image: Image, filter: SamplerFilter): TextureRegion? {
        if (!fits(image.width, image.height)) return null
        // Built first, so a failure here leaves every packer and the page list as they were.
        val ringed = image.withEdgeRing()
        val (page, placement) = placeOnOpenPage(filter, ringed.width, ringed.height) ?: placeOnNewPage(filter, ringed.width, ringed.height)
        write(page.texture, ringed, placement.x, placement.y)
        return TextureRegion(page.texture, x = placement.x + RING, y = placement.y + RING, width = image.width, height = image.height)
    }

    /** Places a [width] by [height] rectangle on the oldest page of [filter] that has room, or returns `null` if none has. */
    private fun placeOnOpenPage(filter: SamplerFilter, width: Int, height: Int): Pair<Page, Placement>? {
        for (page in pages) {
            val placement = if (page.texture.filter == filter) page.packer.insert(width, height) else null
            if (placement != null) return page to placement
        }
        return null
    }

    /** Opens a new page of [filter] and places a [width] by [height] rectangle, which [fits] a page, on it. */
    private fun placeOnNewPage(filter: SamplerFilter, width: Int, height: Int): Pair<Page, Placement> {
        val page = Page(createPage(pageSize, filter), SkylinePacker(pageSize, pageSize))
        pages += page
        // An empty page has room for anything that fits.
        return page to checkNotNull(page.packer.insert(width, height))
    }

    /** Closes every page and forgets them. */
    override fun close() {
        pages.forEach { it.texture.close() }
        pages.clear()
    }

    override fun toString(): String = "AtlasPages(pageSize=$pageSize, pages=$size)"

    companion object {
        /** The width and height of a page in texels: 16 MB of RGBA8, which every desktop GPU accepts. */
        const val PAGE_SIZE: Int = 2048

        /** The width of the ring of repeated edge texels around each image. */
        const val RING: Int = 1
    }
}

/**
 * Returns a copy of this image one texel larger on every side, whose middle holds this image as
 * given and whose border repeats the nearest edge texel, each corner taking the corner texel.
 * Texels are copied, never blended, so transparent and half-transparent edges stay as they are.
 */
internal fun Image.withEdgeRing(): Image {
    val ring = AtlasPages.RING
    val result = Image(width + 2 * ring, height + 2 * ring)
    // The middle, then the top and bottom rows repeated outwards, a row at a time.
    result.copyFrom(this, x = ring, y = ring)
    for (k in 0 until ring) {
        result.copyFrom(this, x = ring, y = k, sourceY = 0, sourceHeight = 1)
        result.copyFrom(this, x = ring, y = ring + height + k, sourceY = height - 1, sourceHeight = 1)
    }
    // The left and right columns, corners included, from the rows just written.
    for (row in 0 until result.height) {
        val left = result[ring, row]
        val right = result[ring + width - 1, row]
        for (k in 0 until ring) {
            result[k, row] = left
            result[ring + width + k, row] = right
        }
    }
    return result
}

/**
 * Creates an RGBA8 GPU texture of [size] by [size] texels, every one transparent, for an atlas
 * page. A render pass that only clears it does this on the GPU, which is cheaper than uploading
 * 16 MB of zeros for a 2048 page; the texture can therefore also be a render target.
 *
 * @throws klyph.platform.SdlException if the texture cannot be created or the clear cannot be submitted.
 */
internal fun GpuDevice.createClearedTexture(size: Int): GpuTexture {
    val texture = createTexture(size, size, usage = TextureUsage.RENDER_TARGET)
    var isCleared = false
    try {
        val commands = acquireCommandBuffer()
        commands.beginRenderPass(texture, Color.TRANSPARENT).end()
        commands.submit()
        isCleared = true
    } finally {
        if (!isCleared) release(texture)
    }
    return texture
}
