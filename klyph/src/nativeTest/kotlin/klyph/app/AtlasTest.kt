package klyph.app

import klyph.platform.Color
import klyph.platform.Files
import klyph.platform.Image
import klyph.platform.SamplerAddress
import klyph.platform.SamplerFilter
import klyph.platform.save
import klyph.render.AtlasPages
import klyph.render.Texture
import klyph.render.TextureRegion
import klyph.render.asRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AtlasTest {
    /** One write into an atlas page through the seam: the page, the image with its ring, and its corner. */
    private class Write(val page: Texture, val image: Image, val x: Int, val y: Int)

    /** What the fake GPU saw: every texture uploaded, pages included, and every write into a page. */
    private class Gpu {
        val uploads = ArrayList<Texture>()
        val pages = ArrayList<Texture>()
        val writes = ArrayList<Write>()
    }

    /**
     * Returns assets over [ROOT] with headless textures and atlas pages of [pageSize] texels, recording
     * into [gpu] what they upload and write after construction.
     */
    private fun assets(gpu: Gpu, pageSize: Int = PAGE_SIZE, atlas: TextureAtlas = TextureAtlas.AUTOMATIC): Assets {
        writeFiles()
        val assets = Assets(
            upload = { image, filter, address -> Texture(image.width, image.height, filter, address).also { gpu.uploads += it } },
            root = ROOT,
            textureFilter = SamplerFilter.LINEAR,
            atlas = atlas,
            pages = AtlasPages(
                pageSize,
                createPage = { size, filter -> Texture(size, size, filter).also { gpu.pages += it } },
                write = { page, image, x, y -> gpu.writes += Write(page, image, x, y) },
            ),
        )
        gpu.uploads.clear() // The built-in font.
        return assets
    }

    /** Writes the fixtures under [ROOT], each texel a color of its own so a misplaced copy shows. */
    private fun writeFiles() {
        patterned(A_WIDTH, A_HEIGHT, seed = 1).save(ROOT + "a.png")
        patterned(B_SIZE, B_SIZE, seed = 2).save(ROOT + "b.png")
        patterned(BIG_SIZE, BIG_SIZE, seed = 3).save(ROOT + "big.png")
        patterned(FILL_SIZE, FILL_SIZE, seed = 4).save(ROOT + "fill.png")
    }

    private fun patterned(width: Int, height: Int, seed: Int): Image {
        val image = Image(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Alpha below 255 on some texels, so a blended copy would differ from a straight one.
                image[x, y] = Color((seed shl 24 or (x shl 16) or (y shl 8) or ((0x80 + x * 3 + y) and 0xFF)).toUInt())
            }
        }
        return image
    }

    /** Returns whether the rectangles of [a] and [b], each grown by the ring, overlap. */
    private fun ringsOverlap(a: TextureRegion, b: TextureRegion): Boolean {
        val ring = AtlasPages.RING
        return a.x - ring < b.x + b.width + ring && b.x - ring < a.x + a.width + ring &&
            a.y - ring < b.y + b.height + ring && b.y - ring < a.y + a.height + ring
    }

    @Test
    fun twoRegionsShareOnePage() {
        val gpu = Gpu()
        val assets = assets(gpu)
        val a = assets.region("a.png")
        val b = assets.region("b.png")
        assertSame(a.texture, b.texture, "Both files land on one page.")
        assertEquals(listOf(a.texture), gpu.pages)
        assertTrue(gpu.uploads.isEmpty(), "No file gets a texture of its own.")
        assertEquals(PAGE_SIZE, a.texture.width)
        assertEquals(SamplerFilter.LINEAR, a.texture.filter)
        assertEquals(SamplerAddress.CLAMP, a.texture.address)
        assertEquals(A_WIDTH, a.width)
        assertEquals(A_HEIGHT, a.height)
        assertEquals(B_SIZE, b.width)
        assertFalse(ringsOverlap(a, b), "$a and $b keep their rings apart.")
        assertEquals(2, gpu.writes.size)
        assertSame(a, assets.region("a.png"), "A second call returns the cached region.")
        assertEquals(2, gpu.writes.size, "A cached region writes nothing.")
        assertTrue(assets.toString().contains("pages=1"), assets.toString())
    }

    @Test
    fun theRingRepeatsTheEdgeTexelsAndTheMiddleIsCopiedAsGiven() {
        val gpu = Gpu()
        val assets = assets(gpu)
        val region = assets.region("a.png")
        val source = patterned(A_WIDTH, A_HEIGHT, seed = 1)
        val write = gpu.writes.single()
        assertSame(region.texture, write.page)
        assertEquals(region.x - 1, write.x)
        assertEquals(region.y - 1, write.y)
        assertEquals(A_WIDTH + 2, write.image.width)
        assertEquals(A_HEIGHT + 2, write.image.height)
        for (y in -1..A_HEIGHT) {
            for (x in -1..A_WIDTH) {
                val expected = source[x.coerceIn(0, A_WIDTH - 1), y.coerceIn(0, A_HEIGHT - 1)]
                assertEquals(expected, write.image[x + 1, y + 1], "Texel ($x, $y) of the entry.")
            }
        }
    }

    @Test
    fun eachFilterHasItsOwnPages() {
        val gpu = Gpu()
        val assets = assets(gpu)
        val linear = assets.region("a.png")
        val nearest = assets.region("a.png", filter = SamplerFilter.NEAREST)
        val nearestB = assets.region("b.png", filter = SamplerFilter.NEAREST)
        assertNotSame(linear.texture, nearest.texture)
        assertEquals(SamplerFilter.NEAREST, nearest.texture.filter)
        assertSame(nearest.texture, nearestB.texture, "A second NEAREST file joins the NEAREST page.")
        assertEquals(listOf(linear.texture, nearest.texture), gpu.pages)
        assertSame(linear, assets.region("a.png", filter = SamplerFilter.LINEAR))
        assertSame(nearest, assets.regionOrNull("a.png", filter = SamplerFilter.NEAREST))
    }

    @Test
    fun repeatAndOversizedFilesGetTextures() {
        val gpu = Gpu()
        val assets = assets(gpu)
        val repeat = assets.region("a.png", address = SamplerAddress.REPEAT)
        assertEquals(SamplerAddress.REPEAT, repeat.texture.address)
        assertEquals(repeat.texture.asRegion(), repeat, "A repeating region covers its own texture.")
        assertSame(assets.texture("a.png", address = SamplerAddress.REPEAT), repeat.texture)

        // With its ring the big file is two texels wider than a page, the fill file exactly as wide.
        val big = assets.region("big.png")
        assertEquals(big.texture.asRegion(), big, "A file too large for a page covers its own texture.")
        assertEquals(SamplerAddress.CLAMP, big.texture.address)
        assertSame(assets.texture("big.png"), big.texture, "The texture cache shares the oversized file's texture.")
        assertSame(big, assets.region("big.png"))
        assertTrue(gpu.pages.isEmpty(), "Neither file opened a page.")
        assertEquals(2, gpu.uploads.size)

        val fill = assets.region("fill.png")
        assertSame(gpu.pages.single(), fill.texture, "A file as large as a page with its ring still fits.")
        assertEquals(1, fill.x)
        assertEquals(1, fill.y)
    }

    @Test
    fun withoutTheAtlasARegionIsTheFilesTexture() {
        val gpu = Gpu()
        val assets = assets(gpu, atlas = TextureAtlas.NONE)
        val region = assets.region("a.png")
        assertEquals(assets.texture("a.png").asRegion(), region)
        assertEquals(assets.texture("b.png").asRegion(), assertNotNull(assets.regionOrNull("b.png")))
        assertTrue(gpu.pages.isEmpty())
        assertTrue(gpu.writes.isEmpty())
        assertEquals(2, gpu.uploads.size)
    }

    @Test
    fun aFullPageOpensAnother() {
        val gpu = Gpu()
        // 5 by 5 texels, 7 with the ring: two fit across and two down a page of 16.
        val assets = assets(gpu, pageSize = 16)
        val regions = (1..5).map { index ->
            val path = "b$index.png"
            patterned(B_SIZE, B_SIZE, seed = 10 + index).save(ROOT + path)
            assets.region(path)
        }
        val first = gpu.pages[0]
        assertEquals(listOf(first, first, first, first), regions.take(4).map { it.texture })
        assertEquals(2, gpu.pages.size, "The fifth file opens a second page.")
        assertSame(gpu.pages[1], regions[4].texture)
        for (i in regions.indices) {
            for (j in i + 1 until regions.size) {
                if (regions[i].texture === regions[j].texture) assertFalse(ringsOverlap(regions[i], regions[j]), "Regions $i and $j.")
            }
        }
    }

    @Test
    fun pagesAreTriedOldestFirst() {
        val gpu = Gpu()
        // 9 by 9 texels, 11 with the ring: one fits a page of 16, and leaves room only for small files.
        val assets = assets(gpu, pageSize = 16)
        patterned(9, 9, seed = 20).save(ROOT + "c1.png")
        patterned(9, 9, seed = 21).save(ROOT + "c2.png")
        Image(1, 1).save(ROOT + "dot.png")
        val c1 = assets.region("c1.png")
        val c2 = assets.region("c2.png")
        assertNotSame(c1.texture, c2.texture)
        assertSame(c1.texture, assets.region("dot.png").texture, "The dot fits in the first page, which is tried before the second.")
        assertEquals(2, gpu.pages.size)
    }

    @Test
    fun closeClosesEveryPage() {
        val gpu = Gpu()
        val assets = assets(gpu)
        assets.region("a.png")
        assets.region("a.png", filter = SamplerFilter.NEAREST)
        assertEquals(2, gpu.pages.size)
        assertTrue(gpu.pages.none { it.isClosed })
        assets.close()
        assertTrue(gpu.pages.all { it.isClosed })
    }

    @Test
    fun regionOrNullSharesTheCache() {
        val gpu = Gpu()
        val assets = assets(gpu)
        val first = assertNotNull(assets.regionOrNull("a.png"))
        assertSame(first, assets.region("a.png"))
        assertSame(first, assets.regionOrNull("a.png"))
        assertEquals(1, gpu.writes.size)
        assertNull(assets.regionOrNull("missing.png"))
        assertEquals(1, gpu.writes.size, "A missing file writes nothing.")
    }

    private companion object {
        val ROOT: String = Files.basePath + "klyph-atlas-test/"

        const val PAGE_SIZE = 32
        const val A_WIDTH = 4
        const val A_HEIGHT = 3
        const val B_SIZE = 5
        const val BIG_SIZE = PAGE_SIZE - 1
        const val FILL_SIZE = PAGE_SIZE - 2
    }
}
