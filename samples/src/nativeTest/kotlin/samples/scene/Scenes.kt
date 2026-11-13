package samples.scene

import klyph.app.Phase
import klyph.scene.Scene
import klyph.scene.buildScene
import klyph.scene.elapsedSeconds
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class Lives(var count: Int) {
    override fun toString(): String = "Lives($count)"
}

class Scenes {
    @Sample
    fun buildScene() {
        val gameOver = buildScene("gameOver") {}
        val play = buildScene("play") {
            onEnter { app -> app.resources.insert(Lives(3)) }
            onExit { app -> app.resources.remove<Lives>() }
            addSystem(Phase.UPDATE) { app ->
                if (app.resources.get<Lives>().count == 0) app.scenes.replace(gameOver)
            }
        }
        assertPrints(play.name, "play")
        assertPrints(play, "Scene(name=play, systems=1)")
        assertPrints(gameOver, "Scene(name=gameOver, systems=0)")

        assertFailsWith<IllegalStateException> {
            buildScene("pause") {
                onEnter {}
                onEnter {}
            }
        }
    }

    @Sample
    fun elapsed() {
        val gameOver = buildScene("gameOver") {}
        lateinit var play: Scene
        play = buildScene("play") {
            addSystem(Phase.UPDATE) { app ->
                // The round ends after one minute of play. The clock stands still while a pause scene is on top.
                if (play.elapsed >= 60.seconds) app.scenes.replace(gameOver)
            }
        }
        // The engine runs the clock while the scene is on top of the stack, so a new scene reads zero.
        assertPrints(play.elapsed, "0s")
        assertPrints(play.elapsedSeconds, "0.0")
    }
}
