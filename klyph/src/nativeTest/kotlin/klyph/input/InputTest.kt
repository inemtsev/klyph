package klyph.input

import klyph.math.Vec2
import klyph.math.length
import klyph.platform.GamepadAxis
import klyph.platform.GamepadButton
import klyph.platform.GamepadId
import klyph.platform.KeyCode
import klyph.platform.MouseButton
import klyph.platform.PlatformEvent
import klyph.platform.Scancode
import klyph.platform.toScancode
import klyph.render.Camera
import klyph.render.lookAt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InputTest {
    private val input = Input()
    private val camera = Camera(zoom = 1f, viewportWidth = 800, viewportHeight = 600).apply { lookAt(400f, 300f) }

    /** Runs one frame of the engine's input update with [events]. */
    private fun frame(vararg events: PlatformEvent) {
        input.beginFrame()
        for (event in events) input.handle(event)
        input.endFrame(camera, pixelDensity = 1f)
    }

    private fun keyDown(scancode: Scancode, isRepeat: Boolean = false) =
        PlatformEvent.KeyDown(KeyCode(0u), isRepeat = isRepeat, scancode = scancode)

    private fun keyUp(scancode: Scancode) = PlatformEvent.KeyUp(KeyCode(0u), scancode)

    private fun assertNear(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 1e-5f, "Expected $expected but was $actual.")

    @Test
    fun pressAndReleaseInOneFrameGiveBothEdges() {
        frame(keyDown(Scancode.SPACE), keyUp(Scancode.SPACE))
        val keyboard = input.keyboard
        assertTrue(keyboard.isJustPressed(Scancode.SPACE))
        assertTrue(keyboard.isJustReleased(Scancode.SPACE))
        assertFalse(keyboard.isDown(Scancode.SPACE))
        frame()
        assertFalse(keyboard.isJustPressed(Scancode.SPACE))
        assertFalse(keyboard.isJustReleased(Scancode.SPACE))
    }

    @Test
    fun heldKeyIsDownButNotJustPressedOnLaterFrames() {
        frame(keyDown(Scancode.W))
        assertTrue(input.keyboard.isDown(Scancode.W))
        assertTrue(input.keyboard.isJustPressed(Scancode.W))
        frame(keyDown(Scancode.W, isRepeat = true))
        assertTrue(input.keyboard.isDown(Scancode.W))
        assertFalse(input.keyboard.isJustPressed(Scancode.W))
        frame(keyUp(Scancode.W))
        assertFalse(input.keyboard.isDown(Scancode.W))
        assertTrue(input.keyboard.isJustReleased(Scancode.W))
        assertFalse(input.keyboard.isDown(Scancode.S))
    }

    @Test
    fun unknownAndOutOfRangeScancodesAreNeverDown() {
        frame(keyDown(Scancode.UNKNOWN), keyDown(Scancode(100_000u)))
        assertFalse(input.keyboard.isDown(Scancode.UNKNOWN))
        assertFalse(input.keyboard.isDown(Scancode(100_000u)))
        assertEquals("Keyboard(down=0)", input.keyboard.toString())
    }

    @Test
    fun focusLossReleasesEverythingHeld() {
        val pad = GamepadId(1u)
        frame(
            keyDown(Scancode.A),
            PlatformEvent.MouseButtonDown(MouseButton.LEFT, 0f, 0f, clicks = 1),
            PlatformEvent.GamepadConnected(pad, "Pad"),
            PlatformEvent.GamepadButtonDown(pad, GamepadButton.SOUTH),
            PlatformEvent.GamepadAxisMoved(pad, GamepadAxis.LEFT_X, 0.8f),
        )
        frame(PlatformEvent.WindowFocusLost)
        val gamepad = input.gamepads.single()
        assertFalse(input.keyboard.isDown(Scancode.A))
        assertTrue(input.keyboard.isJustReleased(Scancode.A))
        assertFalse(input.mouse.isDown(MouseButton.LEFT))
        assertTrue(input.mouse.isJustReleased(MouseButton.LEFT))
        assertFalse(gamepad.isDown(GamepadButton.SOUTH))
        assertTrue(gamepad.isJustReleased(GamepadButton.SOUTH))
        assertEquals(0f, gamepad.axis(GamepadAxis.LEFT_X))
        assertEquals(Vec2.ZERO, gamepad.leftStick)
        assertTrue(gamepad.isConnected)
        frame(PlatformEvent.WindowFocusGained)
        assertFalse(input.keyboard.isJustReleased(Scancode.A))
    }

    @Test
    fun axisCombinesTwoKeys() {
        assertEquals(0f, input.axis(Scancode.A, Scancode.D))
        frame(keyDown(Scancode.A))
        assertEquals(-1f, input.axis(Scancode.A, Scancode.D))
        frame(keyDown(Scancode.D))
        assertEquals(0f, input.axis(Scancode.A, Scancode.D))
        frame(keyUp(Scancode.A))
        assertEquals(1f, input.axis(Scancode.A, Scancode.D))
        frame(keyUp(Scancode.D))
        assertEquals(0f, input.axis(Scancode.A, Scancode.D))
    }

    @Test
    fun mousePositionFollowsTheLastEventAndDeltaAccumulates() {
        val mouse = input.mouse
        assertEquals(Vec2.ZERO, mouse.position)
        frame(PlatformEvent.MouseMoved(10f, 20f, 10f, 20f), PlatformEvent.MouseMoved(15f, 18f, 5f, -2f))
        assertEquals(Vec2(15f, 18f), mouse.position)
        assertEquals(Vec2(15f, 18f), mouse.delta)
        val position = mouse.position
        frame()
        assertSame(position, mouse.position)
        assertEquals(Vec2.ZERO, mouse.delta)
    }

    @Test
    fun wheelResetsEveryFrame() {
        frame(PlatformEvent.MouseWheel(0f, 1f), PlatformEvent.MouseWheel(0.5f, 2f))
        assertEquals(Vec2(0.5f, 3f), input.mouse.wheel)
        frame()
        assertEquals(Vec2.ZERO, input.mouse.wheel)
    }

    @Test
    fun mouseButtonsHaveEdgesAndMoveThePosition() {
        frame(PlatformEvent.MouseButtonDown(MouseButton.RIGHT, 30f, 40f, clicks = 1))
        assertTrue(input.mouse.isDown(MouseButton.RIGHT))
        assertTrue(input.mouse.isJustPressed(MouseButton.RIGHT))
        assertFalse(input.mouse.isDown(MouseButton.LEFT))
        assertEquals(Vec2(30f, 40f), input.mouse.position)
        assertEquals("Mouse(position=Vec2(30.0, 40.0), down=1)", input.mouse.toString())
        frame(PlatformEvent.MouseButtonUp(MouseButton.RIGHT, 30f, 40f))
        assertTrue(input.mouse.isJustReleased(MouseButton.RIGHT))
        assertFalse(input.mouse.isDown(MouseButton.RIGHT))
    }

    @Test
    fun worldPositionGoesThroughTheCameraInPixels() {
        // The camera shows world (0, 0) at the top-left corner, two pixels per point and zoom 2.
        camera.zoom = 2f
        camera.lookAt(200f, 150f)
        input.beginFrame()
        input.handle(PlatformEvent.MouseMoved(100f, 50f, 0f, 0f))
        input.endFrame(camera, pixelDensity = 2f)
        assertEquals(Vec2(100f, 50f), input.mouse.worldPosition)
    }

    @Test
    fun gamepadConnectsWithButtonsAndAxes() {
        val id = GamepadId(5u)
        frame(PlatformEvent.GamepadConnected(id, "Pad"))
        val gamepad = input.gamepadOrNull(id)!!
        assertEquals(listOf(gamepad), input.gamepads)
        assertEquals("Gamepad(id=GamepadId(5), name=Pad, isConnected=true)", gamepad.toString())
        assertNull(input.gamepadOrNull(GamepadId(6u)))

        frame(
            PlatformEvent.GamepadButtonDown(id, GamepadButton.NORTH),
            PlatformEvent.GamepadAxisMoved(id, GamepadAxis.RIGHT_TRIGGER, 0.75f),
        )
        assertTrue(gamepad.isDown(GamepadButton.NORTH))
        assertTrue(gamepad.isJustPressed(GamepadButton.NORTH))
        assertEquals(0.75f, gamepad.axis(GamepadAxis.RIGHT_TRIGGER))
        frame()
        assertFalse(gamepad.isJustPressed(GamepadButton.NORTH))
        assertTrue(gamepad.isDown(GamepadButton.NORTH))
    }

    @Test
    fun sticksApplyARadialDeadZoneAndRescale() {
        val id = GamepadId(1u)
        frame(PlatformEvent.GamepadConnected(id, ""), PlatformEvent.GamepadAxisMoved(id, GamepadAxis.LEFT_X, 0.1f))
        val gamepad = input.gamepads.single()
        assertEquals(Vec2.ZERO, gamepad.leftStick)
        assertEquals(0.1f, gamepad.axis(GamepadAxis.LEFT_X))

        // 0.5 past the default 0.15 dead zone reads (0.5 - 0.15) / (1 - 0.15), about 0.41.
        frame(PlatformEvent.GamepadAxisMoved(id, GamepadAxis.LEFT_X, 0.5f))
        assertNear((0.5f - 0.15f) / 0.85f, gamepad.leftStick.x)
        assertEquals(0f, gamepad.leftStick.y)

        // Radial: each axis alone is inside the dead zone, together they are outside it.
        frame(
            PlatformEvent.GamepadAxisMoved(id, GamepadAxis.RIGHT_X, 0.12f),
            PlatformEvent.GamepadAxisMoved(id, GamepadAxis.RIGHT_Y, -0.12f),
        )
        assertTrue(gamepad.rightStick.x > 0f && gamepad.rightStick.y < 0f)

        frame(
            PlatformEvent.GamepadAxisMoved(id, GamepadAxis.LEFT_X, 1f),
            PlatformEvent.GamepadAxisMoved(id, GamepadAxis.LEFT_Y, 1f),
        )
        assertNear(1f, gamepad.leftStick.length)

        input.deadZone = 0f
        frame(PlatformEvent.GamepadAxisMoved(id, GamepadAxis.LEFT_X, 0.1f), PlatformEvent.GamepadAxisMoved(id, GamepadAxis.LEFT_Y, 0f))
        assertNear(0.1f, gamepad.leftStick.x)
        assertFailsWith<IllegalArgumentException> { input.deadZone = 1f }
        assertFailsWith<IllegalArgumentException> { input.deadZone = -0.1f }
    }

    @Test
    fun disconnectRemovesTheGamepadAndClearsItsState() {
        val first = GamepadId(1u)
        val second = GamepadId(2u)
        frame(
            PlatformEvent.GamepadConnected(first, "One"),
            PlatformEvent.GamepadConnected(second, "Two"),
            PlatformEvent.GamepadButtonDown(first, GamepadButton.SOUTH),
            PlatformEvent.GamepadAxisMoved(first, GamepadAxis.LEFT_X, -1f),
        )
        val gamepad = input.gamepadOrNull(first)!!
        assertEquals(listOf("One", "Two"), input.gamepads.map { it.name })

        frame(PlatformEvent.GamepadDisconnected(first))
        assertEquals(listOf("Two"), input.gamepads.map { it.name })
        assertFalse(gamepad.isConnected)
        assertFalse(gamepad.isDown(GamepadButton.SOUTH))
        assertFalse(gamepad.isJustPressed(GamepadButton.SOUTH))
        assertEquals(0f, gamepad.axis(GamepadAxis.LEFT_X))
        assertEquals(Vec2.ZERO, gamepad.leftStick)
        assertNull(input.gamepadOrNull(first))

        // The same pad plugged back in has a new id and a new entry.
        frame(PlatformEvent.GamepadConnected(GamepadId(3u), "One"))
        assertEquals(listOf("Two", "One"), input.gamepads.map { it.name })
        assertEquals("Input(keysDown=0, mouseButtonsDown=0, gamepads=2)", input.toString())
    }

    @Test
    fun eventsForUnknownGamepadsAreIgnored() {
        frame(
            PlatformEvent.GamepadButtonDown(GamepadId(9u), GamepadButton.SOUTH),
            PlatformEvent.GamepadAxisMoved(GamepadId(9u), GamepadAxis.LEFT_X, 1f),
            PlatformEvent.GamepadDisconnected(GamepadId(9u)),
        )
        assertTrue(input.gamepads.isEmpty())
    }

    // KeyCode.toScancode asks SDL's key map. Without SDL initialized SDL falls back to its default
    // US layout, which is what these tests rely on.
    @Test
    fun keyCodeQueriesGoThroughTheScancode() {
        val scancode = KeyCode.A.toScancode()
        assertEquals(Scancode.A, scancode)
        frame(PlatformEvent.KeyDown(KeyCode.A, isRepeat = false, scancode = scancode))
        assertTrue(input.keyboard.isDown(KeyCode.A))
        assertTrue(input.keyboard.isJustPressed(KeyCode.A))
        assertFalse(input.keyboard.isDown(KeyCode.B))
        frame(PlatformEvent.KeyUp(KeyCode.A, scancode))
        assertTrue(input.keyboard.isJustReleased(KeyCode.A))
    }
}
