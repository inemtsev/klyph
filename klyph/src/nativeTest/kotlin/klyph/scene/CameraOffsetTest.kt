package klyph.scene

import klyph.app.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class CameraOffsetTest {
    @Test
    fun offsetSetOnceIsDrawnThatFrameAndClearedBeforeTheNextInput() {
        val h = SceneHarness()
        h.camera.x = 120f
        h.camera.y = 80f
        var isFirstFrame = true
        val seen = mutableListOf<String>()
        h.addSystem(Phase.INPUT) { seen += "input ${h.camera.offsetX}x${h.camera.offsetY}" }
        h.addSystem(Phase.UPDATE) {
            if (isFirstFrame) {
                h.camera.offsetX = 6f
                h.camera.offsetY = -4f
                isFirstFrame = false
            }
        }
        h.addSystem(Phase.RENDER) { seen += "render ${h.camera.offsetX}x${h.camera.offsetY}" }

        h.frame(16.milliseconds)
        h.frame(16.milliseconds)

        assertEquals(listOf("input 0.0x0.0", "render 6.0x-4.0", "input 0.0x0.0", "render 0.0x0.0"), seen)
        assertEquals(120f, h.camera.x, "Clearing the offset leaves the camera where it is.")
        assertEquals(80f, h.camera.y)
    }

    @Test
    fun scenePushedOverAShakingSceneIsDrawnCentered() {
        val h = SceneHarness()
        val pause = h.scene("pause")
        var isPausePressed = false
        val play = h.scene("play") {
            addSystem(Phase.LATE_UPDATE, h.system { h.camera.offsetX = 10f })
            addSystem(Phase.UPDATE, h.system { if (isPausePressed) h.stack.push(pause) })
        }
        val drawn = mutableListOf<String>()
        h.addSystem(Phase.RENDER) { drawn += "${h.stack.currentOrNull?.name} ${h.camera.offsetX}" }
        h.stack.push(play)
        h.applyTransitions()

        h.frame(16.milliseconds)
        isPausePressed = true
        h.frame(16.milliseconds)
        isPausePressed = false
        h.frame(16.milliseconds)
        h.stack.pop()
        h.frame(16.milliseconds)
        h.frame(16.milliseconds)

        // The frame of the push still draws the shake; the pause, frozen play below it, is centered.
        assertEquals(listOf("play 10.0", "play 10.0", "pause 0.0", "pause 0.0", "play 10.0"), drawn)
    }
}
