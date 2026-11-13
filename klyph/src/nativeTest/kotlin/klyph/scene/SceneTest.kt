package klyph.scene

import klyph.app.Phase
import klyph.app.System
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SceneTest {
    @Test
    fun buildSceneKeepsSystemsPerPhaseInOrder() {
        val a = System {}
        val b = System {}
        val c = System {}
        val scene = buildScene("play") {
            addSystem(Phase.UPDATE, a)
            addSystem(Phase.RENDER, c)
            addSystem(Phase.UPDATE, b)
        }
        assertEquals("play", scene.name)
        assertEquals(listOf(a, b), scene.systemsOf(Phase.UPDATE))
        assertEquals(listOf(c), scene.systemsOf(Phase.RENDER))
        assertEquals(emptyList(), scene.systemsOf(Phase.INPUT))
        assertEquals("Scene(name=play, systems=3)", scene.toString())
    }

    @Test
    fun builderUsedAfterBuildDoesNotChangeTheScene() {
        lateinit var kept: SceneBuilder
        val scene = buildScene("play") {
            kept = this
            addSystem(Phase.UPDATE) {}
        }
        kept.addSystem(Phase.UPDATE) {}
        assertEquals(1, scene.systemsOf(Phase.UPDATE).size)
        assertEquals("SceneBuilder(name=play, systems=2)", kept.toString())
    }

    @Test
    fun settingAHookTwiceThrows() {
        assertFailsWith<IllegalStateException> {
            buildScene("play") {
                onEnter {}
                onEnter {}
            }
        }
        assertFailsWith<IllegalStateException> {
            buildScene("play") {
                onExit {}
                onExit {}
            }
        }
        buildScene("play") {
            onEnter {}
            onExit {}
        }
    }

    @Test
    fun startupAndShutdownSystemsAreRejected() {
        assertFailsWith<IllegalArgumentException> { buildScene("play") { addSystem(Phase.STARTUP) {} } }
        assertFailsWith<IllegalArgumentException> { buildScene("play") { addSystem(Phase.SHUTDOWN) {} } }
    }

    @Test
    fun aNewSceneHasRunForNoTime() {
        val scene = buildScene("play") {}
        assertEquals(Duration.ZERO, scene.elapsed)
        assertEquals(0f, scene.elapsedSeconds)
    }

    @Test
    fun elapsedSecondsIsElapsedInSeconds() {
        val scene = buildScene("play") {}
        scene.elapsed = 1500.milliseconds
        assertEquals(1.5f, scene.elapsedSeconds)
        scene.elapsed = 90.seconds
        assertEquals(90f, scene.elapsedSeconds)
    }

    @Test
    fun toStringAddsElapsedOnceItIsNotZero() {
        val scene = buildScene("play") { addSystem(Phase.UPDATE) {} }
        assertEquals("Scene(name=play, systems=1)", scene.toString())
        scene.elapsed = 12.5.seconds
        assertEquals("Scene(name=play, systems=1, elapsed=12.5s)", scene.toString())
        scene.elapsed = Duration.ZERO
        assertEquals("Scene(name=play, systems=1)", scene.toString())
    }
}
