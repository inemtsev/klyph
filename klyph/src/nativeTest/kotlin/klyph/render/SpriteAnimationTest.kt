package klyph.render

import klyph.entity.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

class SpriteAnimationTest {
    @Test
    fun framesAreTheGivenListNotACopy() {
        val texture = Texture(64, 16)
        val source = listOf(texture.region(0, 0, 16, 16), texture.region(16, 0, 16, 16))
        val animation = SpriteAnimation(source, 100.milliseconds)
        assertSame(source, animation.frames)
        assertEquals(2, animation.animation.frameCount)
        assertEquals(100.milliseconds, animation.animation.frameDuration)
        assertEquals(AnimationMode.LOOP, animation.animation.mode)
    }

    @Test
    fun currentFrameFollowsThePlayhead() {
        val sheet = SpriteSheet.fromGrid(Texture(48, 16), columns = 3, rows = 1)
        val animation = SpriteAnimation(sheet, 100.milliseconds, AnimationMode.ONCE)
        assertSame(sheet.regions, animation.frames)
        assertSame(sheet[0], animation.currentFrame)
        animation.animation.advance(150.milliseconds)
        assertSame(sheet[1], animation.currentFrame)
        animation.animation.advance(1000.milliseconds)
        assertSame(sheet[2], animation.currentFrame)
    }

    @Test
    fun rejectsNoFrames() {
        val error = assertFailsWith<IllegalArgumentException> { SpriteAnimation(emptyList(), 100.milliseconds) }
        assertEquals("Sprite animation frame list is empty.", error.message)
        assertFailsWith<IllegalArgumentException> { SpriteAnimation(SpriteSheet(Texture(8, 8), emptyList()), 100.milliseconds) }
        assertFailsWith<IllegalArgumentException> { SpriteAnimation(listOf(Texture(8, 8).asRegion()), 0.milliseconds) }
    }

    @Test
    fun advanceSetsTheSpriteRegion() {
        val sheet = SpriteSheet.fromGrid(Texture(32, 16), columns = 2, rows = 1)
        val world = World()
        val animated = world.create()
        val sprite = Sprite(sheet[0])
        world.add(animated, Sprite, sprite)
        world.add(animated, SpriteAnimation, SpriteAnimation(sheet, 100.milliseconds))
        val alone = world.create()
        val unanimated = SpriteAnimation(sheet, 100.milliseconds)
        world.add(alone, SpriteAnimation, unanimated)

        world.advanceSpriteAnimations(100.milliseconds)
        assertSame(sheet[1], sprite.region)
        assertEquals(0, unanimated.animation.frameIndex)
        world.advanceSpriteAnimations(100.milliseconds)
        assertSame(sheet[0], sprite.region)
    }

    @Test
    fun toStringShowsFrameAndMode() {
        val sheet = SpriteSheet.fromGrid(Texture(96, 16), columns = 6, rows = 1)
        val animation = SpriteAnimation(sheet, 100.milliseconds, AnimationMode.PING_PONG)
        animation.animation.advance(200.milliseconds)
        assertEquals("SpriteAnimation(frame=2/6, mode=PING_PONG)", animation.toString())
    }
}
