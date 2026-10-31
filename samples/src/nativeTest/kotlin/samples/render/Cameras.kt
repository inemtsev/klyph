package samples.render

import klyph.math.Vec2
import klyph.render.Camera
import klyph.render.lookAt
import klyph.render.screenToWorld
import klyph.render.visibleHeight
import klyph.render.visibleWidth
import klyph.render.worldToScreen
import samples.Sample
import samples.assertPrints

class Cameras {
    @Sample
    fun worldToScreen() {
        val camera = Camera(x = 100f, y = 100f, viewportWidth = 800, viewportHeight = 600)
        assertPrints(camera.worldToScreen(Vec2(100f, 100f)), "Vec2(400.0, 300.0)")
        assertPrints(camera.worldToScreen(Vec2(150f, 100f)), "Vec2(450.0, 300.0)")
        assertPrints(camera.screenToWorld(Vec2(0f, 0f)), "Vec2(-300.0, -200.0)")

        camera.zoom = 2f
        assertPrints(camera.worldToScreen(Vec2(150f, 100f)), "Vec2(500.0, 300.0)")
        assertPrints(camera.screenToWorld(Vec2(500f, 300f)), "Vec2(150.0, 100.0)")
    }

    @Sample
    fun lookAt() {
        val camera = Camera(viewportWidth = 800, viewportHeight = 600)
        camera.lookAt(250f, 80f)
        assertPrints(camera, "Camera(x=250.0, y=80.0, zoom=1.0, rotation=0.0, viewport=800 x 600)")
        assertPrints(camera.worldToScreen(Vec2(250f, 80f)), "Vec2(400.0, 300.0)")
    }

    @Sample
    fun visibleWidth() {
        val camera = Camera(viewportWidth = 800, viewportHeight = 600)
        assertPrints(camera.visibleWidth, "800.0")
        camera.zoom = 2f
        assertPrints(camera.visibleWidth, "400.0")
        assertPrints(camera.visibleHeight, "300.0")
    }
}
