package samples.render

import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.drawnPositionOf
import klyph.math.Rect
import klyph.math.Vec2
import klyph.render.Camera
import klyph.render.clampToBounds
import klyph.render.follow
import klyph.render.lookAt
import klyph.render.screenToWorld
import klyph.render.visibleHeight
import klyph.render.visibleWidth
import klyph.render.worldToScreen
import samples.Sample
import samples.assertPrints
import kotlin.time.Duration.Companion.milliseconds

class Cameras {
    @Sample
    fun worldToScreen() {
        val camera = Camera(x = 100f, y = 100f, viewportWidth = 800f, viewportHeight = 600f)
        assertPrints(camera.worldToScreen(Vec2(100f, 100f)), "Vec2(400.0, 300.0)")
        assertPrints(camera.worldToScreen(Vec2(150f, 100f)), "Vec2(450.0, 300.0)")
        assertPrints(camera.screenToWorld(Vec2(0f, 0f)), "Vec2(-300.0, -200.0)")

        camera.zoom = 2f
        assertPrints(camera.worldToScreen(Vec2(150f, 100f)), "Vec2(500.0, 300.0)")
        assertPrints(camera.screenToWorld(Vec2(500f, 300f)), "Vec2(150.0, 100.0)")
    }

    @Sample
    fun lookAt() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        camera.lookAt(250f, 80f)
        assertPrints(camera, "Camera(x=250.0, y=80.0, zoom=1.0, rotation=0.0, viewport=800.0x600.0, bounds=null)")
        assertPrints(camera.worldToScreen(Vec2(250f, 80f)), "Vec2(400.0, 300.0)")
    }

    @Sample
    fun visibleWidth() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        assertPrints(camera.visibleWidth, "800.0")
        camera.zoom = 2f
        assertPrints(camera.visibleWidth, "400.0")
        assertPrints(camera.visibleHeight, "300.0")
    }

    @Sample
    fun follow() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        val player = Vec2(100f, 0f)
        // Called every frame, the camera eases after the player.
        repeat(60) { camera.follow(player, smoothing = 5f, delta = 16.milliseconds) }
        assertPrints(camera.x > 99f && camera.x < 100f, "true")

        // An Interpolated entity is drawn between its last two fixed steps, so follow the place it is
        // drawn at, not its Transform. In an app, app.drawnPositionOf(hero) passes the frame's own alpha.
        val world = World()
        val hero = world.create()
        world.add(hero, Transform, Transform(x = 300f, y = 40f))
        world.add(hero, Interpolated, Interpolated)
        repeat(120) { camera.follow(world.drawnPositionOf(hero, alpha = 0.5f), smoothing = 5f, delta = 16.milliseconds) }
        assertPrints(camera.x > 299f && camera.x < 300f, "true")
    }

    @Sample
    fun bounds() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        camera.bounds = Rect(x = 0f, y = 0f, width = 2000f, height = 1000f)
        camera.lookAt(0f, 0f)
        assertPrints(camera.x, "400.0")
        assertPrints(camera.y, "300.0")
        camera.x = 5000f
        camera.clampToBounds()
        assertPrints(camera.x, "1600.0")
    }

    @Sample
    fun offset() {
        val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)
        camera.bounds = Rect(x = 0f, y = 0f, width = 2000f, height = 1000f)
        camera.lookAt(0f, 0f)
        assertPrints(camera.worldToScreen(Vec2(400f, 300f)), "Vec2(400.0, 300.0)")

        // One frame of a shake: the picture moves, the camera does not.
        camera.offsetX = -12f
        camera.offsetY = 5f
        assertPrints(camera.worldToScreen(Vec2(400f, 300f)), "Vec2(412.0, 295.0)")
        assertPrints(camera.x, "400.0")

        // Nothing clamps the offset, so the left edge of the screen is now 12 units past the bounds.
        assertPrints(camera.screenToWorld(Vec2(0f, 300f)), "Vec2(-12.0, 305.0)")

        // When the shake ends, the offset goes back to 0.
        camera.offsetX = 0f
        camera.offsetY = 0f
        assertPrints(camera.worldToScreen(Vec2(400f, 300f)), "Vec2(400.0, 300.0)")
    }
}
