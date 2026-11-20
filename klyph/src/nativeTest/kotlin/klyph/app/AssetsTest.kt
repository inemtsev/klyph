package klyph.app

import klyph.platform.FileException
import klyph.platform.Files
import klyph.platform.Image
import klyph.platform.ImageException
import klyph.platform.SamplerAddress
import klyph.platform.SamplerFilter
import klyph.platform.save
import klyph.render.Texture
import klyph.render.asRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AssetsTest {
    /** One request to the fake upload: the image's size and the sampler it was asked for. */
    private data class Upload(val width: Int, val height: Int, val filter: SamplerFilter, val address: SamplerAddress)

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

    /**
     * Writes the fixtures under [ROOT]. [Files] cannot delete, so each one is emptied first: a write
     * that goes missing leaves a file that fails to load, not an old copy that still passes.
     */
    private fun writeFiles() {
        for (name in FIXTURES) Files.writeBytes(ROOT + name, byteArrayOf())
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
            assertEquals(filter, assets.region("rock.png").texture.filter)
            assertEquals(filter, assertNotNull(assets.regionOrNull("rock.png", address = SamplerAddress.REPEAT)).texture.filter)
            assertEquals(filter, assets.spriteSheet("rock.png", columns = 2, rows = 2).texture.filter)
            assertEquals(filter, assertNotNull(assets.spriteSheetOrNull("rock.png", columns = 1, rows = 1)).texture.filter)
            // A font's second load is served from the cache and never reaches its own page upload, so
            // the loaders swap places between the filters and each one's upload is checked in one round.
            val fontOrNullFirst = filter == SamplerFilter.NEAREST
            if (fontOrNullFirst) assertNotNull(assets.fontOrNull("fonts/test.fnt"))
            assets.font("fonts/test.fnt")
            if (!fontOrNullFirst) assertNotNull(assets.fontOrNull("fonts/test.fnt"))
            assertEquals(filter, assets.createTexture(Image(1, 1)).filter)
            assertEquals(
                listOf(
                    ship(filter),
                    Upload(ROCK_SIZE, ROCK_SIZE, filter, SamplerAddress.CLAMP),
                    Upload(ROCK_SIZE, ROCK_SIZE, filter, SamplerAddress.REPEAT),
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
        assets.region("rock.png", filter = SamplerFilter.LINEAR)
        assets.region("rock.png", address = SamplerAddress.REPEAT)
        assets.regionOrNull("rock.png", filter = SamplerFilter.LINEAR, address = SamplerAddress.REPEAT)
        assertEquals(
            listOf(
                ship(SamplerFilter.LINEAR),
                ship(SamplerFilter.NEAREST, SamplerAddress.REPEAT),
                ship(SamplerFilter.LINEAR, SamplerAddress.REPEAT),
                ship(SamplerFilter.LINEAR, SamplerAddress.REPEAT),
                Upload(ROCK_SIZE, ROCK_SIZE, SamplerFilter.LINEAR, SamplerAddress.CLAMP),
                Upload(ROCK_SIZE, ROCK_SIZE, SamplerFilter.NEAREST, SamplerAddress.REPEAT),
                Upload(ROCK_SIZE, ROCK_SIZE, SamplerFilter.LINEAR, SamplerAddress.REPEAT),
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
    fun regionSharesTheTextureCacheAndCoversTheWholeTexture() {
        val uploads = ArrayList<Upload>()
        val assets = assets(uploads)
        val texture = assets.texture("ship.png")
        val region = assets.region("ship.png")
        assertEquals(1, uploads.size, "A region of a loaded file asks no second upload.")
        assertSame(texture, region.texture)
        assertEquals(0, region.x)
        assertEquals(0, region.y)
        assertEquals(texture.width, region.width)
        assertEquals(texture.height, region.height)
        assertEquals(texture.asRegion(), region)
        assertEquals(region, assertNotNull(assets.regionOrNull("ship.png")))
        assertEquals(1, uploads.size)

        val nearest = assets.region("ship.png", filter = SamplerFilter.NEAREST)
        assertNotSame(texture, nearest.texture)
        assertSame(assets.texture("ship.png", filter = SamplerFilter.NEAREST), nearest.texture)
        assertEquals(2, uploads.size, "A region with another filter loads its own texture, once.")

        val first = ArrayList<Upload>()
        val fresh = assets(first)
        val loaded = fresh.region("rock.png")
        assertSame(loaded.texture, fresh.texture("rock.png"))
        assertEquals(1, first.size, "A region loaded first fills the cache for texture.")
    }

    @Test
    fun regionOrNullReturnsNullWhereRegionThrows() {
        val assets = assets(ArrayList())
        Files.writeBytes(ROOT + "broken.png", byteArrayOf(1, 2, 3, 4, 5))
        assertNull(assets.regionOrNull("missing.png"))
        assertNull(assets.regionOrNull("broken.png"))
        assertFailsWith<FileException> { assets.region("missing.png") }
        assertFailsWith<ImageException> { assets.region("broken.png") }
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

    @Test
    fun closeClosesEveryTextureAndEveryLoaderThrowsAfterwards() {
        val assets = assets(ArrayList())
        val textures = mapOf(
            "file" to assets.texture("ship.png"),
            "font page" to assets.font("fonts/test.fnt").pages.single(),
            "built-in font" to assets.defaultFont.pages.single(),
            "disc" to assets.disc(6).texture,
            "image" to assets.createTexture(Image(1, 1)),
        )
        for ((name, texture) in textures) assertFalse(texture.isClosed, "The $name texture is open before close.")

        assets.close()
        for ((name, texture) in textures) assertTrue(texture.isClosed, "The $name texture is closed by close.")
        assets.close() // A second close has no effect.

        val loaders = mapOf<String, () -> Any?>(
            "image" to { assets.image("ship.png") },
            "imageOrNull" to { assets.imageOrNull("ship.png") },
            "texture" to { assets.texture("ship.png") },
            "textureOrNull" to { assets.textureOrNull("ship.png") },
            "region" to { assets.region("ship.png") },
            "regionOrNull" to { assets.regionOrNull("ship.png") },
            "createTexture" to { assets.createTexture(Image(1, 1)) },
            "disc" to { assets.disc(6) },
            "spriteSheet" to { assets.spriteSheet("rock.png", columns = 1, rows = 1) },
            "spriteSheetOrNull" to { assets.spriteSheetOrNull("rock.png", columns = 1, rows = 1) },
            "font" to { assets.font("fonts/test.fnt") },
            "fontOrNull" to { assets.fontOrNull("fonts/test.fnt") },
            "sound" to { assets.sound("sound.wav") },
            "soundOrNull" to { assets.soundOrNull("sound.wav") },
            "musicTrack" to { assets.musicTrack("music.ogg") },
            "musicTrackOrNull" to { assets.musicTrackOrNull("music.ogg") },
        )
        for ((name, load) in loaders) assertFailsWith<IllegalStateException>("$name after close.") { load() }
    }

    private companion object {
        val ROOT: String = Files.basePath + "klyph-assets-test/"

        /** Every file [writeFiles] writes, relative to [ROOT]. */
        val FIXTURES: List<String> = listOf("ship.png", "rock.png", "broken.png", "fonts/page.png", "fonts/test.fnt")

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
