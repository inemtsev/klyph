package klyph.app

import klyph.platform.Files
import klyph.platform.Image
import klyph.platform.SamplerAddress
import klyph.platform.SamplerFilter
import klyph.platform.save
import klyph.render.Texture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class AssetsTest {
    /** One request to the fake upload: the image's size and the sampler it was asked for. */
    private class Upload(val width: Int, val height: Int, val filter: SamplerFilter, val address: SamplerAddress) {
        override fun equals(other: Any?): Boolean =
            other is Upload && other.width == width && other.height == height && other.filter == filter && other.address == address

        override fun hashCode(): Int = ((width * 31 + height) * 31 + filter.hashCode()) * 31 + address.hashCode()

        override fun toString(): String = "Upload(${width}x$height, $filter, $address)"
    }

    /**
     * Returns assets over [ROOT] whose uploads make headless textures and are added to [uploads],
     * the built-in font's at construction included.
     */
    private fun open(uploads: MutableList<Upload>, textureFilter: SamplerFilter): Assets {
        writeFiles()
        return Assets(
            upload = { image, filter, address ->
                uploads += Upload(image.width, image.height, filter, address)
                Texture(image.width, image.height, filter, address)
            },
            root = ROOT,
            textureFilter = textureFilter,
        )
    }

    /** Returns assets as [open] does, with the uploads made at construction removed from [uploads]. */
    private fun assets(uploads: MutableList<Upload>, textureFilter: SamplerFilter = SamplerFilter.LINEAR): Assets {
        val assets = open(uploads, textureFilter)
        uploads.clear()
        return assets
    }

    private fun writeFiles() {
        Image(SHIP_WIDTH, SHIP_HEIGHT).save(ROOT + "ship.png")
        Image(ROCK_SIZE, ROCK_SIZE).save(ROOT + "rock.png")
        Image(PAGE_SIZE, PAGE_SIZE).save(ROOT + "fonts/page.png")
        Files.writeBytes(ROOT + "fonts/test.fnt", FONT.encodeToByteArray())
    }

    private fun ship(filter: SamplerFilter, address: SamplerAddress = SamplerAddress.CLAMP) =
        Upload(SHIP_WIDTH, SHIP_HEIGHT, filter, address)

    @Test
    fun builtInsUploadNearestAndDiscsLinearWhateverTheDefault() {
        for (default in SamplerFilter.entries) {
            val uploads = ArrayList<Upload>()
            val assets = open(uploads, default)
            assertEquals(
                listOf(SamplerFilter.NEAREST to SamplerAddress.CLAMP),
                uploads.map { it.filter to it.address },
                "The built-in font uploads once, at construction, with textureFilter $default.",
            )
            assertEquals(SamplerFilter.NEAREST, assets.whitePixel.texture.filter)
            uploads.clear()
            assets.disc(6)
            assertEquals(listOf(Upload(8, 8, SamplerFilter.LINEAR, SamplerAddress.CLAMP)), uploads, "With textureFilter $default.")
        }
    }

    @Test
    fun textureDefaultsToTheConfiguredFilterAndClamp() {
        val uploads = ArrayList<Upload>()
        val assets = assets(uploads)
        assertEquals(SamplerFilter.LINEAR, assets.textureFilter)
        val texture = assets.texture("ship.png")
        assertEquals(listOf(ship(SamplerFilter.LINEAR)), uploads)
        assertEquals(SamplerFilter.LINEAR, texture.filter)
        assertEquals(SamplerAddress.CLAMP, texture.address)
    }

    @Test
    fun constructorFilterChangesTheDefault() {
        val uploads = ArrayList<Upload>()
        val assets = assets(uploads, SamplerFilter.NEAREST)
        assertEquals(SamplerFilter.NEAREST, assets.textureFilter)
        val texture = assets.texture("ship.png")
        assertEquals(listOf(ship(SamplerFilter.NEAREST)), uploads)
        assertEquals(SamplerFilter.NEAREST, texture.filter)
    }

    @Test
    fun everyLoaderUsesTheSameDefault() {
        for (filter in SamplerFilter.entries) {
            val uploads = ArrayList<Upload>()
            val assets = assets(uploads, filter)
            val page = Upload(PAGE_SIZE, PAGE_SIZE, filter, SamplerAddress.CLAMP)

            assertEquals(filter, assertNotNull(assets.textureOrNull("ship.png")).filter)
            assertEquals(filter, assets.spriteSheet("rock.png", columns = 2, rows = 2).texture.filter)
            assertEquals(filter, assertNotNull(assets.spriteSheetOrNull("rock.png", columns = 1, rows = 1)).texture.filter)
            assets.font("fonts/test.fnt")
            assets.fontOrNull("fonts/test.fnt")
            assertEquals(filter, assets.createTexture(Image(1, 1)).filter)
            assertEquals(
                listOf(
                    ship(filter),
                    Upload(ROCK_SIZE, ROCK_SIZE, filter, SamplerAddress.CLAMP),
                    page,
                    Upload(1, 1, filter, SamplerAddress.CLAMP),
                ),
                uploads,
                "The defaults with textureFilter $filter.",
            )
        }
    }

    @Test
    fun explicitFilterAndAddressOverrideTheDefaults() {
        val uploads = ArrayList<Upload>()
        val assets = assets(uploads, SamplerFilter.NEAREST)
        assets.texture("ship.png", filter = SamplerFilter.LINEAR)
        assets.texture("ship.png", address = SamplerAddress.REPEAT)
        assets.textureOrNull("ship.png", filter = SamplerFilter.LINEAR, address = SamplerAddress.REPEAT)
        assets.createTexture(Image(SHIP_WIDTH, SHIP_HEIGHT), filter = SamplerFilter.LINEAR, address = SamplerAddress.REPEAT)
        assertEquals(
            listOf(
                ship(SamplerFilter.LINEAR),
                ship(SamplerFilter.NEAREST, SamplerAddress.REPEAT),
                ship(SamplerFilter.LINEAR, SamplerAddress.REPEAT),
                ship(SamplerFilter.LINEAR, SamplerAddress.REPEAT),
            ),
            uploads,
        )
    }

    @Test
    fun textureLoadsOncePerPathFilterAndAddress() {
        val uploads = ArrayList<Upload>()
        val assets = assets(uploads)
        val first = assets.texture("ship.png")
        assertSame(first, assets.texture("ship.png"))
        assertSame(first, assets.textureOrNull("ship.png"))
        assertEquals(1, uploads.size, "The same key asks the upload once.")

        val nearest = assets.texture("ship.png", filter = SamplerFilter.NEAREST)
        assertNotSame(first, nearest)
        assertEquals(2, uploads.size)
        val repeat = assets.texture("ship.png", address = SamplerAddress.REPEAT)
        assertNotSame(first, repeat)
        assertNotSame(nearest, repeat)
        assertEquals(3, uploads.size)
        val rock = assets.texture("rock.png")
        assertNotSame(first, rock)
        assertEquals(4, uploads.size)

        assertSame(nearest, assets.texture("ship.png", filter = SamplerFilter.NEAREST))
        assertSame(repeat, assets.texture("ship.png", address = SamplerAddress.REPEAT))
        assertSame(rock, assets.texture("rock.png"))
        assertEquals(4, uploads.size, "Every key is cached after its first load.")
    }

    @Test
    fun createTextureUploadsOnEveryCallAndNeverCaches() {
        val uploads = ArrayList<Upload>()
        val assets = assets(uploads, SamplerFilter.NEAREST)
        val image = Image(SHIP_WIDTH, SHIP_HEIGHT)
        val first = assets.createTexture(image)
        val second = assets.createTexture(image)
        assertNotSame(first, second)
        assertEquals(listOf(ship(SamplerFilter.NEAREST), ship(SamplerFilter.NEAREST)), uploads)

        val loaded = assets.texture("ship.png")
        assertNotSame(first, loaded)
        assertNotSame(second, loaded)
        assertEquals(3, uploads.size, "A texture made from an image does not stand in for the file.")
    }

    private companion object {
        val ROOT: String = Files.basePath + "klyph-assets-test/"
        const val SHIP_WIDTH = 6
        const val SHIP_HEIGHT = 4
        const val ROCK_SIZE = 8
        const val PAGE_SIZE = 16

        val FONT: String = """
            common lineHeight=10 base=8
            page id=0 file="page.png"
            char id=65 x=0 y=0 width=4 height=4 xoffset=0 yoffset=0 xadvance=5 page=0
        """.trimIndent()
    }
}
