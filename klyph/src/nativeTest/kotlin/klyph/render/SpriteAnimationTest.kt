package klyph.render

import klyph.entity.Commands
import klyph.entity.World
import klyph.event.Events
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

class SpriteAnimationTest {
    @Test
    fun framesAreTheGivenListNotACopy() {
        val texture = Texture(64, 16)
        val source = listOf(texture.region(0, 0, 16, 16), texture.region(16, 0, 16, 16))
        val animation = SpriteAnimation(source, 100.milliseconds)
        assertSame(source, animation.frames)
        assertEquals(2, animation.playhead.frameCount)
        assertEquals(100.milliseconds, animation.playhead.frameDuration)
        assertEquals(AnimationMode.LOOP, animation.playhead.mode)
    }

    @Test
    fun currentFrameFollowsThePlayhead() {
        val sheet = SpriteSheet.fromGrid(Texture(48, 16), columns = 3, rows = 1)
        val animation = SpriteAnimation(sheet, 100.milliseconds, AnimationMode.ONCE)
        assertSame(sheet.regions, animation.frames)
        assertSame(sheet[0], animation.currentFrame)
        animation.playhead.advance(150.milliseconds)
        assertSame(sheet[1], animation.currentFrame)
        animation.playhead.advance(1000.milliseconds)
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
        assertEquals(0, unanimated.playhead.frameIndex)
        world.advanceSpriteAnimations(100.milliseconds)
        assertSame(sheet[0], sprite.region)
    }

    @Test
    fun toStringShowsFrameAndMode() {
        val sheet = SpriteSheet.fromGrid(Texture(96, 16), columns = 6, rows = 1)
        val animation = SpriteAnimation(sheet, 100.milliseconds, AnimationMode.PING_PONG)
        animation.playhead.advance(200.milliseconds)
        assertEquals("SpriteAnimation(frame=2/6, mode=PING_PONG, speed=1.0, end=HOLD)", animation.toString())
    }

    @Test
    fun speedIsThePlayheadsAndTheEngineAdvancesByIt() {
        val sheet = SpriteSheet.fromGrid(Texture(96, 16), columns = 6, rows = 1)
        val world = World()
        val entity = world.create()
        val sprite = Sprite(sheet[0])
        val animation = SpriteAnimation(sheet, 100.milliseconds)
        world.add(entity, Sprite, sprite)
        world.add(entity, SpriteAnimation, animation)

        animation.speed = 3f
        assertEquals(3f, animation.playhead.speed)
        world.advanceSpriteAnimations(100.milliseconds)
        assertSame(sheet[3], sprite.region)
        assertFailsWith<IllegalArgumentException> { animation.speed = -0.5f }
        assertEquals("SpriteAnimation(frame=3/6, mode=LOOP, speed=3.0, end=HOLD)", animation.toString())
    }

    /** Returns the [AnimationFinished] events published so far this frame. */
    private fun Events.finished(): List<AnimationFinished> {
        val list = ArrayList<AnimationFinished>()
        forEach<AnimationFinished> { list += it }
        return list
    }

    @Test
    fun onceAnimationPublishesFinishedExactlyOnce() {
        val sheet = SpriteSheet.fromGrid(Texture(32, 16), columns = 2, rows = 1)
        val world = World()
        val events = Events()
        val commands = Commands(world)
        val entity = world.create()
        world.add(entity, Sprite, Sprite(sheet[0]))
        val animation = SpriteAnimation(sheet, 100.milliseconds, AnimationMode.ONCE)
        world.add(entity, SpriteAnimation, animation)

        world.advanceSpriteAnimations(150.milliseconds, null, events, commands)
        assertEquals(emptyList(), events.finished())
        world.advanceSpriteAnimations(50.milliseconds, null, events, commands)
        assertEquals(listOf(AnimationFinished(entity)), events.finished(), "The last frame's time is up at 200 ms.")
        events.endFrame()
        world.advanceSpriteAnimations(100.milliseconds, null, events, commands)
        assertEquals(emptyList(), events.finished(), "A finished animation publishes once.")
        assertTrue(commands.isEmpty(), "HOLD keeps the entity.")
        assertTrue(world.isAlive(entity))

        animation.playhead.restart()
        world.advanceSpriteAnimations(200.milliseconds, null, events, commands)
        assertEquals(listOf(AnimationFinished(entity)), events.finished(), "A restarted animation publishes again when it finishes.")
    }

    @Test
    fun destroyEndQueuesTheEntityWhenTheAnimationFinishes() {
        val sheet = SpriteSheet.fromGrid(Texture(32, 16), columns = 2, rows = 1)
        val world = World()
        val events = Events()
        val commands = Commands(world)
        val entity = world.create()
        world.add(entity, Sprite, Sprite(sheet[0]))
        world.add(entity, SpriteAnimation, SpriteAnimation(sheet, 100.milliseconds, AnimationMode.ONCE, AnimationEnd.DESTROY))

        world.advanceSpriteAnimations(199.milliseconds, null, events, commands)
        assertTrue(commands.isEmpty())
        world.advanceSpriteAnimations(1.milliseconds, null, events, commands)
        assertEquals(1, commands.size)
        commands.apply()
        assertFalse(world.isAlive(entity))
        assertEquals(listOf(AnimationFinished(entity)), events.finished(), "The event names the entity even though it is gone.")
    }

    @Test
    fun loopingAnimationsNeverFinish() {
        val sheet = SpriteSheet.fromGrid(Texture(32, 16), columns = 2, rows = 1)
        val world = World()
        val events = Events()
        val commands = Commands(world)
        val entity = world.create()
        world.add(entity, Sprite, Sprite(sheet[0]))
        world.add(entity, SpriteAnimation, SpriteAnimation(sheet, 100.milliseconds, end = AnimationEnd.DESTROY))
        repeat(10) { world.advanceSpriteAnimations(100.milliseconds, null, events, commands) }
        assertEquals(emptyList(), events.finished())
        assertTrue(commands.isEmpty(), "DESTROY has no effect on a LOOP animation.")
    }

    @Test
    fun animationFinishedEqualityAndToString() {
        val world = World()
        val entity = world.create()
        assertEquals(AnimationFinished(entity), AnimationFinished(entity))
        assertEquals(AnimationFinished(entity).hashCode(), AnimationFinished(entity).hashCode())
        assertEquals("AnimationFinished(entity=$entity)", AnimationFinished(entity).toString())
    }
}
