// Snippets of docs/performance.md. They compile with the samples but never run, because they need
// a running app. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.collision.Shape
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.math.Vec2
import klyph.math.length
import klyph.render.Sprite
import kotlin.math.sqrt

class Performance {
    fun App.chaseSlow(player: Entity) {
        // snippet: allocating
        // Allocates four Vec2 objects per enemy per step.
        addSystem(Phase.FIXED_UPDATE) { app ->
            val target = app.world.get(player, Transform)
            app.world.forEach(Transform, Enemy) { _, transform, _ ->
                val toTarget = Vec2(target.x, target.y) - Vec2(transform.x, transform.y)
                if (toTarget.length > 1f) {
                    val step = toTarget * (150f / toTarget.length * app.time.deltaSeconds)
                    transform.x += step.x
                    transform.y += step.y
                }
            }
        }
        // end snippet
    }

    fun App.chaseFast(player: Entity) {
        // snippet: floats
        // The same motion on plain floats allocates nothing.
        addSystem(Phase.FIXED_UPDATE) { app ->
            val target = app.world.get(player, Transform)
            val dt = app.time.deltaSeconds
            app.world.forEach(Transform, Enemy) { _, transform, _ ->
                val dx = target.x - transform.x
                val dy = target.y - transform.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > 1f) {
                    transform.x += dx / distance * 150f * dt
                    transform.y += dy / distance * 150f * dt
                }
            }
        }
        // end snippet
    }

    fun App.reuseCollections() {
        // snippet: reuse
        // Created once, filled every frame.
        val nearby = ArrayList<Entity>()
        val probe = Shape.Circle(64f)
        addSystem(Phase.UPDATE) { app ->
            app.world.forEach(Transform, Sprite) { _, transform, sprite ->
                nearby.clear()
                app.collisions.overlappingTo(nearby, probe, transform.x, transform.y)
                // Hide instead of destroying and respawning: no component stores change.
                sprite.isVisible = nearby.size < 8
            }
        }
        // end snippet
    }

    fun App.watchStats() {
        // snippet: stats
        addSystem(Phase.UPDATE) { app ->
            val stats = app.stats
            if (app.time.frameCount % 300L == 0L) {
                println(
                    "frame ${stats.total}, update ${stats.durationOf(Phase.UPDATE)}, extract ${stats.spriteExtraction}, " +
                        "${stats.spriteCount} sprites in ${stats.drawCallCount} draw calls",
                )
            }
        }
        // end snippet
    }
}
