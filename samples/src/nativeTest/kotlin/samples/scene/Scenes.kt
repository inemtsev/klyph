package samples.scene

import klyph.app.Phase
import klyph.scene.buildScene
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith

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
}
