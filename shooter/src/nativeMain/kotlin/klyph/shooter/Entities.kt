package klyph.shooter

import klyph.entity.Commands
import klyph.entity.Entity
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.platform.Color
import klyph.render.AnimationMode
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Factories for every kind of entity in the game. They queue the entity on App.commands, so they
// are safe to call inside a World.forEach; the components attach after the running system.
// Everything moved in FIXED_UPDATE is Interpolated, so it is drawn smoothly between steps.

/** Scatters dim stars of a few sizes over the whole arena, behind everything else. */
fun Commands.spawnStars(art: Art, random: Random) {
    repeat(Tuning.STAR_COUNT) {
        spawn {
            val size = random.nextFloat(Tuning.STAR_MIN_SIZE, Tuning.STAR_MAX_SIZE)
            val alpha = random.nextFloat(Tuning.STAR_MIN_ALPHA, Tuning.STAR_MAX_ALPHA)
            // A few stars lean blue, so the field is not flat grey.
            val blue = if (random.nextInt(4) == 0) 1f else 0.85f
            add(Transform, Transform(random.nextFloat(0f, Tuning.ARENA_WIDTH), random.nextFloat(0f, Tuning.ARENA_HEIGHT)))
            add(Sprite, Sprite(art.star, size, size, Color(0.85f, 0.9f, blue, alpha), layer = Tuning.LAYER_STARS))
        }
    }
}

/** Creates the player's ship at the center of the arena, facing up. */
fun Commands.spawnPlayer(art: Art): Entity = spawn {
    add(Transform, Transform(Tuning.ARENA_WIDTH / 2, Tuning.ARENA_HEIGHT / 2))
    add(Sprite, Sprite(art.ship, Tuning.SHIP_SIZE, Tuning.SHIP_SIZE, layer = Tuning.LAYER_SHIPS))
    add(Player, Player())
    add(Interpolated, Interpolated)
}

/** Fires a bullet from ([x], [y]) along [heading], in radians clockwise from straight up. */
fun Commands.spawnBullet(art: Art, x: Float, y: Float, heading: Float) {
    spawn {
        add(Transform, Transform(x, y, rotation = heading))
        add(Sprite, Sprite(art.bullet, Tuning.BULLET_WIDTH, Tuning.BULLET_HEIGHT, layer = Tuning.LAYER_BULLETS))
        add(Velocity, Velocity(Tuning.BULLET_SPEED * sin(heading), -Tuning.BULLET_SPEED * cos(heading)))
        add(Lifetime, Lifetime(Tuning.BULLET_LIFETIME))
        add(Bullet, Bullet())
        add(Interpolated, Interpolated)
    }
}

/** Spawns an enemy just above the top edge of the arena at a random x, drifting down. */
fun Commands.spawnEnemy(art: Art, random: Random) {
    spawn {
        val margin = Tuning.ENEMY_SWAY_AMPLITUDE + Tuning.SHIP_SIZE / 2
        val centerX = random.nextFloat(margin, Tuning.ARENA_WIDTH - margin)
        val phase = random.nextFloat(0f, 2f * PI.toFloat())
        add(Transform, Transform(centerX, -Tuning.SHIP_SIZE / 2))
        add(Sprite, Sprite(art.enemy, Tuning.SHIP_SIZE, Tuning.SHIP_SIZE, layer = Tuning.LAYER_SHIPS))
        add(Velocity, Velocity(0f, random.nextFloat(Tuning.ENEMY_MIN_SPEED, Tuning.ENEMY_MAX_SPEED)))
        add(Enemy, Enemy(centerX, phase, random.nextFloat(Tuning.ENEMY_SWAY_MIN_RATE, Tuning.ENEMY_SWAY_MAX_RATE)))
        add(Interpolated, Interpolated)
    }
}

/** Plays one explosion at ([x], [y]), above ships and bullets. */
fun Commands.spawnExplosion(art: Art, x: Float, y: Float) {
    spawn {
        val animation = SpriteAnimation(art.explosion, Tuning.EXPLOSION_FRAME, AnimationMode.ONCE)
        add(Transform, Transform(x, y))
        add(Sprite, Sprite(animation.currentFrame, Tuning.EXPLOSION_SIZE, Tuning.EXPLOSION_SIZE, layer = Tuning.LAYER_EFFECTS))
        add(SpriteAnimation, animation)
        add(Explosion, Explosion())
    }
}

/** Returns a random float in [from] until [until]. */
private fun Random.nextFloat(from: Float, until: Float): Float = from + nextFloat() * (until - from)
