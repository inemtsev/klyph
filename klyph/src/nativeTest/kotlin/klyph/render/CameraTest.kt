package klyph.render

import klyph.math.Rect
import klyph.math.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-3f

private fun assertNear(expected: Vec2, actual: Vec2) {
    assertTrue(
        abs(expected.x - actual.x) <= TOLERANCE && abs(expected.y - actual.y) <= TOLERANCE,
        "Expected $expected but was $actual.",
    )
}

/** Applies the column-major [matrix] to world point [world] and converts clip space to pixels. */
private fun throughMatrix(matrix: FloatArray, world: Vec2, width: Float, height: Float): Vec2 {
    val clipX = matrix[0] * world.x + matrix[4] * world.y + matrix[12]
    val clipY = matrix[1] * world.x + matrix[5] * world.y + matrix[13]
    val clipW = matrix[3] * world.x + matrix[7] * world.y + matrix[15]
    assertEquals(1f, clipW, "w component")
    return Vec2((clipX + 1f) * 0.5f * width, (1f - clipY) * 0.5f * height)
}

class CameraTest {
    @Test
    fun identityCentersWorldOrigin() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        assertNear(Vec2(400f, 300f), camera.worldToScreen(Vec2(0f, 0f)))
        assertNear(Vec2(800f, 600f), camera.worldToScreen(Vec2(400f, 300f)))
    }

    @Test
    fun positionMovesViewCenter() {
        val camera = Camera(x = 100f, y = 100f, viewportWidth = 800f, viewportHeight = 600f)
        assertNear(Vec2(400f, 300f), camera.worldToScreen(Vec2(100f, 100f)))
        assertNear(Vec2(390f, 320f), camera.worldToScreen(Vec2(90f, 120f)))
    }

    @Test
    fun zoomScalesAroundCenter() {
        val camera = Camera(zoom = 2f, viewportWidth = 800f, viewportHeight = 600f)
        assertNear(Vec2(600f, 300f), camera.worldToScreen(Vec2(100f, 0f)))
        assertEquals(400f, camera.visibleWidth)
        assertEquals(300f, camera.visibleHeight)
    }

    @Test
    fun quarterTurnMovesPositiveXUpOnScreen() {
        // The camera turns like an entity, so the world turns the other way: world +x, which is
        // screen right at rotation 0, appears above the center (screen -y).
        val camera = Camera(rotation = (PI / 2).toFloat(), viewportWidth = 800f, viewportHeight = 600f)
        assertNear(Vec2(400f, 200f), camera.worldToScreen(Vec2(100f, 0f)))
        assertNear(Vec2(500f, 300f), camera.worldToScreen(Vec2(0f, 100f)))
    }

    @Test
    fun spriteWithCameraRotationAppearsUpright() {
        // A sprite's local +x axis in world space is (cos r, sin r), as in sprite.vert.hlsl.
        val rotation = 0.7f
        val camera = Camera(rotation = rotation, viewportWidth = 800f, viewportHeight = 600f)
        val axis = Vec2(kotlin.math.cos(rotation), kotlin.math.sin(rotation)) * 100f
        assertNear(Vec2(500f, 300f), camera.worldToScreen(axis))
    }

    @Test
    fun screenToWorldInvertsWorldToScreen() {
        val camera = Camera(x = -35f, y = 12.5f, zoom = 1.75f, rotation = 2.1f, viewportWidth = 1280f, viewportHeight = 720f)
        val points = listOf(Vec2(0f, 0f), Vec2(123f, -456f), Vec2(-35f, 12.5f), Vec2(999f, 3f))
        for (world in points) {
            assertNear(world, camera.screenToWorld(camera.worldToScreen(world)))
        }
        for (screen in listOf(Vec2(0f, 0f), Vec2(1280f, 720f), Vec2(17f, 600f))) {
            assertNear(screen, camera.worldToScreen(camera.screenToWorld(screen)))
        }
    }

    @Test
    fun projectionAgreesWithWorldToScreen() {
        val matrix = FloatArray(16) { 99f }
        val cameras = listOf(
            Camera(viewportWidth = 800f, viewportHeight = 600f),
            Camera(x = 100f, y = -50f, zoom = 2f, viewportWidth = 800f, viewportHeight = 600f),
            Camera(x = -35f, y = 12.5f, zoom = 0.5f, rotation = 2.1f, viewportWidth = 1280f, viewportHeight = 720f),
            Camera(x = 3f, y = 4f, zoom = 3f, rotation = (PI / 2).toFloat(), viewportWidth = 640f, viewportHeight = 480f),
        )
        val points = listOf(Vec2(0f, 0f), Vec2(123f, -456f), Vec2(-35f, 12.5f), Vec2(400f, 300f))
        for (camera in cameras) {
            camera.writeProjection(matrix)
            assertEquals(1f, matrix[10], "z scale for $camera")
            for (index in listOf(2, 3, 6, 7, 8, 9, 11, 14)) {
                assertEquals(0f, matrix[index], "matrix[$index] for $camera")
            }
            for (world in points) {
                val expected = camera.worldToScreen(world)
                assertNear(expected, throughMatrix(matrix, world, camera.viewportWidth, camera.viewportHeight))
            }
        }
    }

    @Test
    fun identityProjectionMatchesPointOrtho() {
        // Rotation 0 and zoom 1 reduce to the renderer's ortho matrix, shifted by half the view.
        val matrix = FloatArray(16)
        Camera(x = 400f, y = 300f, viewportWidth = 800f, viewportHeight = 600f).writeProjection(matrix)
        assertEquals(2f / 800f, matrix[0])
        assertEquals(-2f / 600f, matrix[5])
        assertEquals(-1f, matrix[12])
        assertEquals(1f, matrix[13])
        assertEquals(1f, matrix[15])
    }

    @Test
    fun projectionRejectsShortDestination() {
        assertFailsWith<IllegalArgumentException> { Camera().writeProjection(FloatArray(15)) }
    }

    @Test
    fun lookAtSetsPosition() {
        val camera = Camera(zoom = 2f, rotation = 1f)
        camera.lookAt(5f, -7f)
        assertEquals(5f, camera.x)
        assertEquals(-7f, camera.y)
        assertEquals(2f, camera.zoom)
        assertEquals(1f, camera.rotation)
    }

    @Test
    fun zoomMustBePositiveAndFinite() {
        assertFailsWith<IllegalArgumentException> { Camera(zoom = 0f) }
        assertFailsWith<IllegalArgumentException> { Camera(zoom = -1f) }
        assertFailsWith<IllegalArgumentException> { Camera(zoom = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { Camera(zoom = Float.POSITIVE_INFINITY) }
        val camera = Camera()
        assertFailsWith<IllegalArgumentException> { camera.zoom = 0f }
        assertFailsWith<IllegalArgumentException> { camera.zoom = -2f }
        assertEquals(1f, camera.zoom)
        camera.zoom = 0.25f
        assertEquals(0.25f, camera.zoom)
    }

    @Test
    fun viewportMustBePositive() {
        assertFailsWith<IllegalArgumentException> { Camera(viewportWidth = 0f) }
        assertFailsWith<IllegalArgumentException> { Camera(viewportHeight = -1f) }
        val camera = Camera()
        assertEquals(1f, camera.viewportWidth)
        assertEquals(1f, camera.viewportHeight)
        assertFailsWith<IllegalArgumentException> { camera.viewportWidth = 0f }
        camera.viewportWidth = 1920f
        camera.viewportHeight = 1080f
        assertEquals(1920f, camera.viewportWidth)
        assertEquals(1080f, camera.viewportHeight)
    }

    @Test
    fun toStringListsState() {
        val camera = Camera(x = 1f, y = 2f, zoom = 3f, rotation = 0.5f, viewportWidth = 800f, viewportHeight = 600f)
        assertEquals("Camera(x=1.0, y=2.0, zoom=3.0, rotation=0.5, viewport=800.0x600.0, bounds=null)", camera.toString())
    }

    @Test
    fun followCoversTheExponentialShareOfTheDistance() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        camera.follow(100f, -50f, smoothing = 2f, delta = 500.milliseconds)
        val share = 1f - exp(-1f)
        assertNear(Vec2(100f * share, -50f * share), Vec2(camera.x, camera.y))
    }

    @Test
    fun followConvergesTheSameWayAtAnyFrameRate() {
        val coarse = Camera(viewportWidth = 800f, viewportHeight = 600f)
        val fine = Camera(viewportWidth = 800f, viewportHeight = 600f)
        repeat(10) { coarse.follow(Vec2(200f, 100f), smoothing = 5f, delta = 100.milliseconds) }
        repeat(100) { fine.follow(Vec2(200f, 100f), smoothing = 5f, delta = 10.milliseconds) }
        assertTrue(abs(coarse.x - fine.x) < 0.05f && abs(coarse.y - fine.y) < 0.05f, "Coarse ${coarse.x}, fine ${fine.x}.")
        assertTrue(abs(200f - coarse.x) < 2f, "After a second at a smoothing of 5 the camera is within 1% of the target, at ${coarse.x}.")
    }

    @Test
    fun followAtZeroStaysAndAtInfinityJumps() {
        val camera = Camera(x = 10f, y = 10f, viewportWidth = 800f, viewportHeight = 600f)
        camera.follow(100f, 100f, smoothing = 0f, delta = 1.seconds)
        assertNear(Vec2(10f, 10f), Vec2(camera.x, camera.y))
        camera.follow(100f, 100f, smoothing = Float.POSITIVE_INFINITY, delta = Duration.ZERO)
        assertNear(Vec2(100f, 100f), Vec2(camera.x, camera.y))
        assertFailsWith<IllegalArgumentException> { camera.follow(0f, 0f, smoothing = -1f, delta = 1.seconds) }
        assertFailsWith<IllegalArgumentException> { camera.follow(0f, 0f, smoothing = Float.NaN, delta = 1.seconds) }
        assertFailsWith<IllegalArgumentException> { camera.follow(0f, 0f, smoothing = 1f, delta = (-1).seconds) }
    }

    @Test
    fun boundsClampTheVisibleArea() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        camera.bounds = Rect(0f, 0f, 2000f, 1000f)
        camera.lookAt(0f, 0f)
        assertNear(Vec2(400f, 300f), Vec2(camera.x, camera.y))
        camera.lookAt(5000f, 5000f)
        assertNear(Vec2(1600f, 700f), Vec2(camera.x, camera.y))
        camera.lookAt(1000f, 500f)
        assertNear(Vec2(1000f, 500f), Vec2(camera.x, camera.y))

        // Written directly, the position stays as written until something clamps it.
        camera.x = -100f
        assertEquals(-100f, camera.x)
        camera.clampToBounds()
        assertEquals(400f, camera.x)

        // Zoomed in, the visible area is smaller, so the camera may go nearer the edge.
        camera.zoom = 2f
        camera.lookAt(0f, 0f)
        assertNear(Vec2(200f, 150f), Vec2(camera.x, camera.y))
    }

    @Test
    fun boundsNarrowerThanTheViewCentreTheCamera() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        camera.bounds = Rect(100f, 0f, 400f, 2000f)
        camera.lookAt(0f, 0f)
        assertNear(Vec2(300f, 300f), Vec2(camera.x, camera.y))
        camera.follow(1000f, 1000f, smoothing = Float.POSITIVE_INFINITY, delta = 1.seconds)
        assertNear(Vec2(300f, 1000f), Vec2(camera.x, camera.y))
        camera.bounds = null
        camera.lookAt(-5f, -5f)
        assertNear(Vec2(-5f, -5f), Vec2(camera.x, camera.y))
    }

    @Test
    fun offsetIsZeroByDefault() {
        val camera = Camera()
        assertEquals(0f, camera.offsetX)
        assertEquals(0f, camera.offsetY)
    }

    @Test
    fun offsetShiftsTheViewAfterTheBoundsClampAndIsNeverClamped() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        camera.bounds = Rect(0f, 0f, 2000f, 1000f)
        camera.lookAt(0f, 0f)
        camera.offsetX = -50f
        camera.offsetY = 30f
        camera.clampToBounds()

        // The clamp leaves the camera at the edge of the bounds, and the offset as it was.
        assertNear(Vec2(400f, 300f), Vec2(camera.x, camera.y))
        assertEquals(-50f, camera.offsetX)
        assertEquals(30f, camera.offsetY)
        // The view is centred on (350, 330), so its left edge is 50 units past the bounds.
        assertNear(Vec2(450f, 270f), camera.worldToScreen(Vec2(400f, 300f)))
        assertNear(Vec2(-50f, 330f), camera.screenToWorld(Vec2(0f, 300f)))
    }

    @Test
    fun lookAtAndFollowNeverReadOrChangeTheOffset() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        camera.offsetX = 25f
        camera.offsetY = -25f

        camera.lookAt(100f, 200f)
        assertEquals(Vec2(100f, 200f), Vec2(camera.x, camera.y))
        repeat(100) { camera.follow(Vec2(300f, 400f), smoothing = 5f, delta = 100.milliseconds) }
        assertNear(Vec2(300f, 400f), Vec2(camera.x, camera.y))
        assertEquals(25f, camera.offsetX)
        assertEquals(-25f, camera.offsetY)
    }

    @Test
    fun aCameraWithAnOffsetSeesWhatAMovedCameraSees() {
        val shaken = Camera(x = 100f, y = 50f, zoom = 2f, rotation = 0.7f, viewportWidth = 800f, viewportHeight = 600f)
        shaken.offsetX = 10f
        shaken.offsetY = -5f
        val moved = Camera(x = 110f, y = 45f, zoom = 2f, rotation = 0.7f, viewportWidth = 800f, viewportHeight = 600f)
        val shakenMatrix = FloatArray(16)
        val movedMatrix = FloatArray(16)
        shaken.writeProjection(shakenMatrix)
        moved.writeProjection(movedMatrix)

        for (index in 0 until 16) assertEquals(movedMatrix[index], shakenMatrix[index], TOLERANCE, "matrix[$index]")
        for (world in listOf(Vec2(0f, 0f), Vec2(123f, -456f), Vec2(110f, 45f))) {
            assertNear(moved.worldToScreen(world), shaken.worldToScreen(world))
            assertNear(shaken.worldToScreen(world), throughMatrix(shakenMatrix, world, 800f, 600f))
        }
        for (screen in listOf(Vec2(0f, 0f), Vec2(800f, 600f), Vec2(17f, 321f))) {
            assertNear(moved.screenToWorld(screen), shaken.screenToWorld(screen))
            assertNear(screen, shaken.worldToScreen(shaken.screenToWorld(screen)))
        }
    }

    @Test
    fun toStringShowsTheOffsetOnlyWhileItIsNotZero() {
        val camera = Camera(x = 1f, y = 2f, zoom = 3f, rotation = 0.5f, viewportWidth = 800f, viewportHeight = 600f)
        val plain = "Camera(x=1.0, y=2.0, zoom=3.0, rotation=0.5, viewport=800.0x600.0, bounds=null"
        assertEquals("$plain)", camera.toString())

        camera.offsetX = 6f
        assertEquals("$plain, offsetX=6.0, offsetY=0.0)", camera.toString())
        camera.offsetY = -4f
        assertEquals("$plain, offsetX=6.0, offsetY=-4.0)", camera.toString())

        camera.offsetX = 0f
        camera.offsetY = 0f
        assertEquals("$plain)", camera.toString())
    }
}
