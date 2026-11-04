package klyph.render

import klyph.entity.Commands
import klyph.entity.World
import klyph.platform.Color
import klyph.text.Text
import klyph.text.testFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FadeTest {
    private val region = Texture(8, 8).asRegion()

    @Test
    fun fadesFromTheAlphaItFoundToTheTarget() {
        val world = World()
        val commands = Commands(world)
        val entity = world.create()
        val sprite = Sprite(region, color = Color(red = 1f, green = 0.5f, blue = 0f, alpha = 0.6f))
        world.add(entity, Sprite, sprite)
        world.add(entity, Fade, Fade(1.seconds))

        world.advanceFades(500.milliseconds, commands)
        // Halfway from 0.6 to 0: 0.3, red, green and blue unchanged.
        assertEquals(0.3f, sprite.color.alpha, 1f / 255f)
        assertEquals(1f, sprite.color.red)
        assertEquals(0.5f, sprite.color.green, 1f / 255f)

        world.advanceFades(500.milliseconds, commands)
        assertEquals(0f, sprite.color.alpha)
        assertTrue(world.get(entity, Fade).isFinished)
        assertTrue(commands.isEmpty(), "HOLD keeps the entity.")

        sprite.color = Color.WHITE
        world.advanceFades(500.milliseconds, commands)
        assertEquals(Color.WHITE, sprite.color, "A finished fade leaves the colors alone.")
    }

    @Test
    fun waitsForTheDelayAndFadesTextToo() {
        val world = World()
        val commands = Commands(world)
        val entity = world.create()
        val text = Text(testFont(), "A")
        world.add(entity, Text, text)
        val fade = Fade(200.milliseconds, target = 0.5f, delay = 300.milliseconds)
        world.add(entity, Fade, fade)

        world.advanceFades(300.milliseconds, commands)
        assertEquals(1f, text.color.alpha, "Nothing changes during the delay.")
        world.advanceFades(100.milliseconds, commands)
        assertEquals(0.75f, text.color.alpha, 1f / 255f)
        world.advanceFades(1.seconds, commands)
        assertEquals(0.5f, text.color.alpha, 1f / 255f)
        assertEquals(500.milliseconds, fade.elapsed)
    }

    @Test
    fun destroyEndQueuesTheEntityOnceTheFadeIsDone() {
        val world = World()
        val commands = Commands(world)
        val entity = world.create()
        world.add(entity, Sprite, Sprite(region))
        world.add(entity, Fade, Fade(100.milliseconds, end = FadeEnd.DESTROY))

        world.advanceFades(50.milliseconds, commands)
        assertTrue(commands.isEmpty())
        world.advanceFades(50.milliseconds, commands)
        assertEquals(1, commands.size)
        commands.apply()
        assertFalse(world.isAlive(entity))
    }

    @Test
    fun zeroDurationJumpsToTheTarget() {
        val world = World()
        val commands = Commands(world)
        val entity = world.create()
        val sprite = Sprite(region)
        world.add(entity, Sprite, sprite)
        world.add(entity, Fade, Fade(Duration.ZERO, target = 0.2f))
        world.advanceFades(Duration.ZERO, commands)
        assertEquals(0.2f, sprite.color.alpha, 1f / 255f)
    }

    @Test
    fun rejectsBadSettingsAndPrintsItsProgress() {
        assertFailsWith<IllegalArgumentException> { Fade((-1).seconds) }
        assertFailsWith<IllegalArgumentException> { Fade(1.seconds, delay = Duration.INFINITE) }
        val error = assertFailsWith<IllegalArgumentException> { Fade(1.seconds, target = 2f) }
        assertEquals("Fade target 2.0 is outside 0.0..1.0.", error.message)
        assertEquals("Fade(elapsed=0.00s, duration=1s, delay=0s, target=0.0, end=HOLD)", Fade(1.seconds).toString())
    }
}
