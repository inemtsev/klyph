package klyph.scene

import klyph.app.Phase
import klyph.math.Vec2
import klyph.platform.PlatformEvent
import klyph.render.screenToWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class CameraOffsetTest {
    @Test
    fun offsetSetOnceIsStillDrawnTwoFramesLater() {
        val h = SceneHarness()
        h.camera.x = 120f
        h.camera.y = 80f
        var isFirstFrame = true
        val drawn = mutableListOf<String>()
        h.addSystem(Phase.UPDATE) {
            if (isFirstFrame) {
                h.camera.offsetX = 6f
                h.camera.offsetY = -4f
                isFirstFrame = false
            }
        }
        h.addSystem(Phase.RENDER) { drawn += "${h.camera.offsetX}x${h.camera.offsetY}" }

        h.frame(16.milliseconds)
        h.frame(16.milliseconds)
        h.frame(16.milliseconds)

        assertEquals(listOf("6.0x-4.0", "6.0x-4.0", "6.0x-4.0"), drawn)
        assertEquals(120f, h.camera.x, "The offset leaves the camera's position alone.")
        assertEquals(80f, h.camera.y)
    }

    @Test
    fun scenePushedOverAShakingSceneIsDrawnCentered() {
        val h = SceneHarness()
        val pause = h.scene("pause")
        val play = h.scene("play") {
            addSystem(Phase.LATE_UPDATE, h.system { h.camera.offsetX = 10f })
        }
        val drawn = mutableListOf<String>()
        h.addSystem(Phase.RENDER) { drawn += "${h.stack.currentOrNull?.name} ${h.camera.offsetX}" }
        h.stack.push(play)
        h.applyTransitions()

        h.frame(16.milliseconds)
        h.stack.push(pause)
        h.frame(16.milliseconds)
        h.frame(16.milliseconds)
        h.frame(16.milliseconds)

        // The frame of the push still draws the shake; the transition zeroes it, and frozen play no longer sets it.
        assertEquals(listOf("play 10.0", "play 10.0", "pause 0.0", "pause 0.0"), drawn)
    }

    @Test
    fun popZeroesTheOffsetUntilTheUncoveredSceneSetsItAgain() {
        val h = SceneHarness()
        var isShaking = false
        val pause = h.scene("pause") {
            addSystem(Phase.LATE_UPDATE, h.system { h.camera.offsetX = 3f })
        }
        val play = h.scene("play") {
            addSystem(Phase.LATE_UPDATE, h.system { if (isShaking) h.camera.offsetX = 10f })
        }
        val drawn = mutableListOf<String>()
        h.addSystem(Phase.INPUT) { drawn += "input ${h.camera.offsetX}" }
        h.addSystem(Phase.RENDER) { drawn += "${h.stack.currentOrNull?.name} ${h.camera.offsetX}" }
        h.stack.push(play)
        h.stack.push(pause)
        h.applyTransitions()

        h.frame(16.milliseconds)
        h.stack.pop()
        h.frame(16.milliseconds)
        h.frame(16.milliseconds)
        isShaking = true
        h.frame(16.milliseconds)

        assertEquals(
            listOf(
                "input 0.0", "pause 3.0",
                "input 3.0", "pause 3.0",
                "input 0.0", "play 0.0",
                "input 0.0", "play 10.0",
            ),
            drawn,
        )
    }

    @Test
    fun mouseWorldPositionAgreesWithScreenToWorldWhileTheOffsetIsHeld() {
        val h = SceneHarness()
        h.camera.x = 400f
        h.camera.y = 300f
        var isFirstFrame = true
        h.addSystem(Phase.UPDATE) {
            if (isFirstFrame) {
                h.camera.offsetX = 6f
                isFirstFrame = false
            }
        }
        h.frame(16.milliseconds)
        h.input.handle(PlatformEvent.MouseMoved(400f, 300f, 0f, 0f))
        val seen = mutableListOf<String>()
        val record = { phase: String -> seen += "$phase ${h.input.mouse.worldPosition} ${h.camera.screenToWorld(h.input.mouse.position)}" }
        h.addSystem(Phase.INPUT) { record("input") }
        h.addSystem(Phase.RENDER) { record("render") }

        h.frame(16.milliseconds)

        val under = Vec2(406f, 300f)
        assertEquals(listOf("input $under $under", "render $under $under"), seen)
    }

    @Test
    fun offsetSetInOnEnterSurvivesTheTransitionThatEnteredTheScene() {
        val h = SceneHarness()
        val menu = h.scene("menu")
        val play = h.scene("play", enter = {
            h.camera.offsetX = 8f
            h.camera.offsetY = -2f
        })
        val drawn = mutableListOf<String>()
        h.addSystem(Phase.RENDER) { drawn += "${h.stack.currentOrNull?.name} ${h.camera.offsetX}x${h.camera.offsetY}" }
        h.stack.push(menu)
        h.applyTransitions()

        h.stack.push(play)
        h.frame(16.milliseconds)
        h.frame(16.milliseconds)
        h.frame(16.milliseconds)

        // The transition zeroes the offset first, then runs onEnter, so what onEnter set is what is drawn.
        assertEquals(listOf("menu 0.0x0.0", "play 8.0x-2.0", "play 8.0x-2.0"), drawn)
    }

    @Test
    fun replaceZeroesTheHeldOffsetBeforeTheIncomingSceneEnters() {
        val h = SceneHarness()
        val seenOnEnter = mutableListOf<Float>()
        val play = h.scene("play", enter = { h.camera.offsetX = 10f })
        val over = h.scene("over", enter = { seenOnEnter += h.camera.offsetX })
        val drawn = mutableListOf<String>()
        h.addSystem(Phase.RENDER) { drawn += "${h.stack.currentOrNull?.name} ${h.camera.offsetX}" }
        h.stack.push(play)
        h.applyTransitions()

        h.frame(16.milliseconds)
        h.stack.replace(over)
        h.frame(16.milliseconds)
        h.frame(16.milliseconds)

        assertEquals(listOf(0f), seenOnEnter, "The incoming scene's onEnter sees a zeroed offset.")
        assertEquals(listOf("play 10.0", "play 10.0", "over 0.0"), drawn)
    }
}
