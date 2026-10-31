package klyph.render

import klyph.math.Vec2
import kotlin.math.PI
import kotlin.math.abs
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
private fun throughMatrix(matrix: FloatArray, world: Vec2, width: Int, height: Int): Vec2 {
    val clipX = matrix[0] * world.x + matrix[4] * world.y + matrix[12]
    val clipY = matrix[1] * world.x + matrix[5] * world.y + matrix[13]
    val clipW = matrix[3] * world.x + matrix[7] * world.y + matrix[15]
    assertEquals(1f, clipW, "w component")
    return Vec2((clipX + 1f) * 0.5f * width, (1f - clipY) * 0.5f * height)
}

class CameraTest {
    @Test
    fun identityCentersWorldOrigin() {
        val camera = Camera(viewportWidth = 800, viewportHeight = 600)
        assertNear(Vec2(400f, 300f), camera.worldToScreen(Vec2(0f, 0f)))
        assertNear(Vec2(800f, 600f), camera.worldToScreen(Vec2(400f, 300f)))
    }

    @Test
    fun positionMovesViewCenter() {
        val camera = Camera(x = 100f, y = 100f, viewportWidth = 800, viewportHeight = 600)
        assertNear(Vec2(400f, 300f), camera.worldToScreen(Vec2(100f, 100f)))
        assertNear(Vec2(390f, 320f), camera.worldToScreen(Vec2(90f, 120f)))
    }

    @Test
    fun zoomScalesAroundCenter() {
        val camera = Camera(zoom = 2f, viewportWidth = 800, viewportHeight = 600)
        assertNear(Vec2(600f, 300f), camera.worldToScreen(Vec2(100f, 0f)))
        assertEquals(400f, camera.visibleWidth)
        assertEquals(300f, camera.visibleHeight)
    }

    @Test
    fun quarterTurnMovesPositiveXUpOnScreen() {
        // The camera turns like an entity, so the world turns the other way: world +x, which is
        // screen right at rotation 0, appears above the center (screen -y).
        val camera = Camera(rotation = (PI / 2).toFloat(), viewportWidth = 800, viewportHeight = 600)
        assertNear(Vec2(400f, 200f), camera.worldToScreen(Vec2(100f, 0f)))
        assertNear(Vec2(500f, 300f), camera.worldToScreen(Vec2(0f, 100f)))
    }

    @Test
    fun spriteWithCameraRotationAppearsUpright() {
        // A sprite's local +x axis in world space is (cos r, sin r), as in sprite.vert.hlsl.
        val rotation = 0.7f
        val camera = Camera(rotation = rotation, viewportWidth = 800, viewportHeight = 600)
        val axis = Vec2(kotlin.math.cos(rotation), kotlin.math.sin(rotation)) * 100f
        assertNear(Vec2(500f, 300f), camera.worldToScreen(axis))
    }

    @Test
    fun screenToWorldInvertsWorldToScreen() {
        val camera = Camera(x = -35f, y = 12.5f, zoom = 1.75f, rotation = 2.1f, viewportWidth = 1280, viewportHeight = 720)
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
            Camera(viewportWidth = 800, viewportHeight = 600),
            Camera(x = 100f, y = -50f, zoom = 2f, viewportWidth = 800, viewportHeight = 600),
            Camera(x = -35f, y = 12.5f, zoom = 0.5f, rotation = 2.1f, viewportWidth = 1280, viewportHeight = 720),
            Camera(x = 3f, y = 4f, zoom = 3f, rotation = (PI / 2).toFloat(), viewportWidth = 640, viewportHeight = 480),
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
    fun identityProjectionMatchesPixelOrtho() {
        // Rotation 0 and zoom 1 reduce to the renderer's ortho matrix, shifted by half the view.
        val matrix = FloatArray(16)
        Camera(x = 400f, y = 300f, viewportWidth = 800, viewportHeight = 600).writeProjection(matrix)
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
        assertFailsWith<IllegalArgumentException> { Camera(viewportWidth = 0) }
        assertFailsWith<IllegalArgumentException> { Camera(viewportHeight = -1) }
        val camera = Camera()
        assertEquals(1, camera.viewportWidth)
        assertEquals(1, camera.viewportHeight)
        assertFailsWith<IllegalArgumentException> { camera.viewportWidth = 0 }
        camera.viewportWidth = 1920
        camera.viewportHeight = 1080
        assertEquals(1920, camera.viewportWidth)
        assertEquals(1080, camera.viewportHeight)
    }

    @Test
    fun toStringListsState() {
        val camera = Camera(x = 1f, y = 2f, zoom = 3f, rotation = 0.5f, viewportWidth = 800, viewportHeight = 600)
        assertEquals("Camera(x=1.0, y=2.0, zoom=3.0, rotation=0.5, viewport=800 x 600)", camera.toString())
    }
}
