package klyph.render

import klyph.entity.World
import klyph.platform.SamplerAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpriteTilingTest {
    /** A 16 by 16 texture that repeats, the kind a tiled sprite needs. */
    private val ground = Texture(16, 16, address = SamplerAddress.REPEAT)

    /**
     * Returns u0, v0, u1 and v1 as the renderer writes them into the record of [sprite], which the
     * first entity of a new world has, so a failure names `Entity(0:0)`.
     */
    private fun uvOf(sprite: Sprite): List<Float> {
        val world = World()
        world.add(world.create(), Sprite, sprite)
        val uv = FloatArray(4)
        sprite.tiledUv(world, uv)
        return uv.toList()
    }

    private fun tiled(
        width: Float,
        height: Float,
        tileWidth: Float,
        tileHeight: Float,
        isFlippedX: Boolean = false,
        isFlippedY: Boolean = false,
    ) = Sprite(
        ground.asRegion(),
        width,
        height,
        isFlippedX = isFlippedX,
        isFlippedY = isFlippedY,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
    )

    @Test
    fun spritesAreNotTiledByDefault() {
        val sprite = Sprite(ground.asRegion())
        assertEquals(0f, sprite.tileWidth)
        assertEquals(0f, sprite.tileHeight)
    }

    @Test
    fun aTileSizeRepeatsTheTextureAcrossTheQuad() {
        // 80 by 48 units of 16 by 16 tiles: 5 across, 3 down.
        assertEquals(listOf(0f, 0f, 5f, 3f), uvOf(tiled(width = 80f, height = 48f, tileWidth = 16f, tileHeight = 16f)))
    }

    @Test
    fun theTileSizeIsInTheSpritesOwnUnitsAndMayDifferPerAxis() {
        // 32 units wide tiles in 80 units: 2.5; 8 units tall tiles in 48 units: 6.
        assertEquals(listOf(0f, 0f, 2.5f, 6f), uvOf(tiled(width = 80f, height = 48f, tileWidth = 32f, tileHeight = 8f)))
    }

    @Test
    fun aCountThatIsNotWholeCutsTheLastTile() {
        assertEquals(listOf(0f, 0f, 2.5f, 1.5f), uvOf(tiled(width = 40f, height = 24f, tileWidth = 16f, tileHeight = 16f)))
    }

    @Test
    fun aTileSizeOnOneAxisLeavesTheOtherStretched() {
        assertEquals(listOf(0f, 0f, 5f, 1f), uvOf(tiled(width = 80f, height = 48f, tileWidth = 16f, tileHeight = 0f)))
        assertEquals(listOf(0f, 0f, 1f, 3f), uvOf(tiled(width = 80f, height = 48f, tileWidth = 0f, tileHeight = 16f)))
    }

    @Test
    fun flipsSwapTheWrappedPairs() {
        assertEquals(listOf(5f, 0f, 0f, 3f), uvOf(tiled(80f, 48f, 16f, 16f, isFlippedX = true)))
        assertEquals(listOf(0f, 3f, 5f, 0f), uvOf(tiled(80f, 48f, 16f, 16f, isFlippedY = true)))
        assertEquals(listOf(5f, 3f, 0f, 0f), uvOf(tiled(80f, 48f, 16f, 16f, isFlippedX = true, isFlippedY = true)))
    }

    @Test
    fun aTiledSpriteOnATextureThatClampsNamesTheEntityTheTextureAndTheRule() {
        val sprite = Sprite(Texture(16, 16).asRegion(), width = 32f, tileWidth = 16f)
        val error = assertFailsWith<IllegalStateException> { uvOf(sprite) }
        assertEquals(
            "Entity(0:0) has a Sprite with a tile size, which needs a texture created with SamplerAddress.REPEAT, " +
                "but Texture(16x16) uses CLAMP.",
            error.message,
        )
    }

    @Test
    fun aTiledSpriteOnAPartOfATextureNamesTheEntityTheRegionAndTheRule() {
        val sprite = Sprite(ground.region(x = 0, y = 0, width = 8, height = 8), tileHeight = 8f)
        val error = assertFailsWith<IllegalStateException> { uvOf(sprite) }
        assertEquals(
            "Entity(0:0) has a Sprite with a tile size, which needs a region that covers its whole texture, " +
                "but its region is TextureRegion(x=0, y=0, size=8x8, texture=Texture(16x16, REPEAT)).",
            error.message,
        )
    }

    @Test
    fun theMessageNamesTheEntityThatHasTheSprite() {
        val world = World()
        world.add(world.create(), Sprite, Sprite(ground.asRegion()))
        world.add(world.create(), Sprite, tiled(width = 32f, height = 32f, tileWidth = 16f, tileHeight = 16f))
        val clamped = Sprite(Texture(16, 16).asRegion(), tileWidth = 16f)
        world.add(world.create(), Sprite, clamped)
        val error = assertFailsWith<IllegalStateException> { clamped.tiledUv(world, FloatArray(4)) }
        assertTrue(error.message.orEmpty().startsWith("Entity(2:0) has a Sprite with a tile size"), "Got ${error.message}.")
    }

    @Test
    fun aSpriteOfNoEntityGetsAGenericName() {
        val clamped = Sprite(Texture(16, 16).asRegion(), tileWidth = 16f)
        val error = assertFailsWith<IllegalStateException> { clamped.tiledUv(World(), FloatArray(4)) }
        assertTrue(error.message.orEmpty().startsWith("Unknown entity has a Sprite with a tile size"), "Got ${error.message}.")
    }

    @Test
    fun theTextureIsCheckedBeforeTheRegion() {
        val sprite = Sprite(Texture(16, 16).region(x = 0, y = 0, width = 8, height = 8), tileWidth = 8f)
        val error = assertFailsWith<IllegalStateException> { uvOf(sprite) }
        assertTrue("SamplerAddress.REPEAT" in error.message.orEmpty(), "Got ${error.message}.")
    }

    @Test
    fun aNegativeInfiniteOrNotANumberTileSizeIsRejected() {
        val negative = assertFailsWith<IllegalStateException> { uvOf(tiled(80f, 48f, tileWidth = -16f, tileHeight = 16f)) }
        assertEquals("Entity(0:0) has a Sprite with tile size -16.0x16.0, which is negative, infinite or not a number.", negative.message)
        val notANumber = assertFailsWith<IllegalStateException> { uvOf(tiled(80f, 48f, tileWidth = 0f, tileHeight = Float.NaN)) }
        assertEquals("Entity(0:0) has a Sprite with tile size 0.0xNaN, which is negative, infinite or not a number.", notANumber.message)
        val infinite = assertFailsWith<IllegalStateException> {
            uvOf(tiled(80f, 48f, tileWidth = Float.POSITIVE_INFINITY, tileHeight = 16f))
        }
        assertEquals(
            "Entity(0:0) has a Sprite with tile size Infinityx16.0, which is negative, infinite or not a number.",
            infinite.message,
        )
    }

    @Test
    fun tileSizeAppearsInToStringOnlyWhenSet() {
        val region = "region=TextureRegion(x=0, y=0, size=16x16, texture=Texture(16x16, REPEAT))"
        assertEquals(
            "Sprite(size=80.0x48.0, tile=16.0x8.0, color=Color(#ffffffff), layer=0, flippedX=false, flippedY=false, " +
                "visible=true, $region)",
            tiled(80f, 48f, 16f, 8f).toString(),
        )
        assertEquals(
            "Sprite(size=16.0x16.0, color=Color(#ffffffff), layer=0, flippedX=false, flippedY=false, visible=true, $region)",
            Sprite(ground.asRegion()).toString(),
        )
    }
}
