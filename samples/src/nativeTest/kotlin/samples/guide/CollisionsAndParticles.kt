// Snippets of docs/collisions-and-particles.md. They compile with the samples but never run,
// because they need a running app. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.Phase
import klyph.collision.Collider
import klyph.collision.CollisionLayers
import klyph.collision.Overlap
import klyph.collision.Shape
import klyph.collision.contains
import klyph.collision.otherThan
import klyph.collision.overlapping
import klyph.entity.ComponentType
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.math.degrees
import klyph.particle.EmitterEnd
import klyph.particle.EmitterSpace
import klyph.particle.ParticleEmitter
import klyph.platform.Color
import klyph.platform.Scancode
import klyph.render.Sprite
import kotlin.math.PI
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// snippet: layers
/** The collision layers of the game, one bit each. */
object Layers {
    val PLAYER: CollisionLayers = CollisionLayers.of(0)
    val ENEMY: CollisionLayers = CollisionLayers.of(1)
    val BULLET: CollisionLayers = CollisionLayers.of(2)
}

/** Marks a bullet. A marker carries no data, so the component is the object itself. */
object Bullet : ComponentType<Bullet>("Bullet")

/** Marks an enemy. */
object Enemy : ComponentType<Enemy>("Enemy")
// end snippet

class CollisionsAndParticles {
    fun App.spawnBodies() {
        // snippet: colliders
        val disc = assets.disc(24)
        commands.spawn {
            add(Transform, Transform(x = 400f, y = 100f))
            add(Sprite, Sprite(disc, color = Color(1f, 0.3f, 0.3f)))
            add(Enemy, Enemy)
            // On the enemy layer; hits the player and bullets.
            add(Collider, Collider(Shape.Circle(12f), layers = Layers.ENEMY, mask = Layers.PLAYER + Layers.BULLET))
        }
        commands.spawn {
            add(Transform, Transform(x = 400f, y = 600f))
            add(Bullet, Bullet)
            add(Sprite, Sprite(assets.whitePixel, width = 4f, height = 12f))
            // Hits enemies only, so bullets pass through each other and through the player.
            add(Collider, Collider(Shape.Box(width = 4f, height = 12f), layers = Layers.BULLET, mask = Layers.ENEMY))
        }
        // end snippet
    }

    fun App.resolveHits(player: Entity) {
        // snippet: overlaps
        addSystem(Phase.UPDATE) { app ->
            val world = app.world
            app.events.forEach<Overlap> { overlap ->
                if (player in overlap) println("The player touched ${overlap.otherThan(player)}")
                val bullet = when {
                    world.has(overlap.a, Bullet) -> overlap.a
                    world.has(overlap.b, Bullet) -> overlap.b
                    else -> return@forEach
                }
                val target = overlap.otherThan(bullet)
                // An earlier pair this frame may have destroyed either one already.
                if (world.isAlive(bullet) && world.isAlive(target) && world.has(target, Enemy)) {
                    app.commands.destroy(bullet)
                    app.commands.destroy(target)
                }
            }
        }
        // end snippet
    }

    fun App.bomb(player: Entity) {
        // snippet: queries
        val inBlast = ArrayList<Entity>()
        addSystem(Phase.UPDATE) { app ->
            if (!app.input.keyboard.isJustPressed(Scancode.B)) return@addSystem
            val center = app.world.get(player, Transform)
            // Reuses one list instead of allocating a new one per query.
            inBlast.clear()
            app.collisions.overlappingTo(inBlast, Shape.Circle(150f), center.x, center.y, mask = Layers.ENEMY)
            for (enemy in inBlast) app.commands.destroy(enemy)

            val nearest = app.collisions.firstOverlappingOrNull(Shape.Box(width = 40f, height = 40f), center.x, center.y)
            val all = app.collisions.overlapping(Shape.Circle(400f), center.x, center.y)
            println("Destroyed ${inBlast.size}, first in reach: $nearest, ${all.size} within 400 units")
        }
        // end snippet
    }

    fun App.explode(x: Float, y: Float) {
        // snippet: burst
        val sparks = ParticleEmitter(assets.disc(8), capacity = 64).apply {
            speed = 80f..260f
            lifetime = 300.milliseconds..700.milliseconds
            gravityY = 200f
            startColor = Color(1f, 0.8f, 0.3f)
            endColor = Color(0.8f, 0.1f, 0f, 0f)
            startScale = 1f
            endScale = 0.2f
            layer = 2
            // The entity goes away once the last spark has faded.
            whenEmpty = EmitterEnd.DESTROY
            emit(64)
        }
        commands.spawn {
            add(Transform, Transform(x = x, y = y))
            add(ParticleEmitter, sparks)
        }
        // end snippet
    }

    fun App.addThruster(ship: Entity) {
        // snippet: thruster
        val exhaust = ParticleEmitter(assets.disc(6), capacity = 200).apply {
            rate = 120f
            // Out of the ship's tail: down the screen while the ship faces up, turning with it.
            space = EmitterSpace.LOCAL
            direction = (PI / 2).toFloat()
            spread = 20f.degrees
            speed = 60f..120f
            lifetime = 0.2.seconds..0.4.seconds
            startRotation = 0f..(2 * PI).toFloat()
            angularVelocity = -3f..3f
            startColor = Color(0.5f, 0.8f, 1f)
        }
        commands.spawn {
            setParent(ship)
            add(Transform, Transform(y = 40f))
            add(ParticleEmitter, exhaust)
        }
        addSystem(Phase.UPDATE) { app ->
            // Only while thrusting; the particles already out fly on and fade.
            exhaust.rate = if (app.input.keyboard.isDown(Scancode.W)) 120f else 0f
        }
        // end snippet
    }
}
