package klyph.render

import klyph.entity.World
import klyph.platform.SamplerAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpriteOriginTest {
    private val region = Texture(16, 16).asRegion()

    @Test
    fun aSpriteIsCenteredOnItsTransformByDefault() {
        val sprite = Sprite(region)
        assertEquals(0.5f, sprite.originX)
        assertEquals(0.5f, sprite.originY)
    }

    @Test
    fun theOriginCanBeChangedAfterTheSpriteIsMade() {
        val sprite = Sprite(region, originX = 0.25f)
        sprite.originY = 1f
        assertEquals(0.25f, sprite.originX)
        assertEquals(1f, sprite.originY)
    }

    @Test
    fun theOriginAppearsInToStringOnlyWhenItIsNotTheCenter() {
        val shown = "color=Color(#ffffffff), layer=0, flippedX=false, flippedY=false, visible=true"
        val texture = "texture=Texture(16x16)"
        assertEquals("Sprite(size=16.0x16.0, $shown, region=TextureRegion(x=0, y=0, size=16x16, $texture))", Sprite(region).toString())
        assertEquals(
            "Sprite(size=16.0x16.0, origin=0.5x1.0, $shown, region=TextureRegion(x=0, y=0, size=16x16, $texture))",
            Sprite(region, originY = 1f).toString(),
        )
        assertEquals(
            "Sprite(size=16.0x16.0, origin=0.0x0.5, $shown, region=TextureRegion(x=0, y=0, size=16x16, $texture))",
            Sprite(region, originX = 0f).toString(),
        )
        val ground = Texture(16, 16, address = SamplerAddress.REPEAT).asRegion()
        assertEquals(
            "Sprite(size=80.0x16.0, origin=0.0x1.0, tile=16.0x0.0, $shown, " +
                "region=TextureRegion(x=0, y=0, size=16x16, texture=Texture(16x16, REPEAT)))",
            Sprite(ground, width = 80f, tileWidth = 16f, originX = 0f, originY = 1f).toString(),
        )
    }

    @Test
    fun anyFiniteOriginPassesTheCheck() {
        val world = World()
        for (origin in listOf(0f, 0.5f, 1f, -3f, 40f, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_VALUE)) {
            Sprite(region, originX = origin, originY = origin).checkOrigin(world)
        }
    }

    @Test
    fun aSpriteOfNoEntityGetsAGenericNameInTheMessage() {
        val sprite = Sprite(region, originY = Float.NEGATIVE_INFINITY)
        val error = assertFailsWith<IllegalStateException> { sprite.checkOrigin(World()) }
        assertEquals("Unknown entity has a Sprite with origin 0.5x-Infinity, which is infinite or not a number.", error.message)
    }
}
