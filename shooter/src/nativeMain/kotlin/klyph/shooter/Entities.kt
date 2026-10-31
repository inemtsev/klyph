package klyph.shooter

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.render.AnimationMode
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import kotlin.math.PI
import kotlin.random.Random

// Factories for every kind of entity in the game. None of them may run inside a World.forEach,
// because adding components can grow the stores that query is walking.

/** Scatters dim stars of a few sizes over the whole arena, behind everything else. */
fun World.spawnStars(art: Art, random: Random) {
    repeat(Tuning.STAR_COUNT) {
        val size = random.nextFloat(Tuning.STAR_MIN_SIZE, Tuning.STAR_MAX_SIZE)
        val alpha = random.nextFloat(Tuning.STAR_MIN_ALPHA, Tuning.STAR_MAX_ALPHA)
        // A few stars lean blue, so the field is not flat grey.
        val blue = if (random.nextInt(4) == 0) 1f else 0.85f
        val star = create()
        add(star, Transform, Transform(random.nextFloat(0f, Tuning.ARENA_WIDTH), random.nextFloat(0f, Tuning.ARENA_HEIGHT)))
        add(star, Sprite, Sprite(art.star, size, size, Color(0.85f, 0.9f, blue, alpha), layer = Tuning.LAYER_STARS))
    }
}

/** Creates the player's ship at the center of the arena. */
fun World.spawnPlayer(art: Art): Entity {
    val sprite = Sprite(art.ship, Tuning.SHIP_SIZE, Tuning.SHIP_SIZE, layer = Tuning.LAYER_SHIPS)
    val ship = create()
    add(ship, Transform, Transform(Tuning.ARENA_WIDTH / 2, Tuning.ARENA_HEIGHT / 2))
    add(ship, Sprite, sprite)
    add(ship, Player, Player(sprite))
    return ship
}

/** Fires a bullet upward from ([x], [y]). */
fun World.spawnBullet(art: Art, x: Float, y: Float) {
    val bullet = create()
    add(bullet, Transform, Transform(x, y))
    add(bullet, Sprite, Sprite(art.bullet, Tuning.BULLET_WIDTH, Tuning.BULLET_HEIGHT, layer = Tuning.LAYER_BULLETS))
    add(bullet, Velocity, Velocity(0f, -Tuning.BULLET_SPEED))
    add(bullet, Lifetime, Lifetime(Tuning.BULLET_LIFETIME))
    add(bullet, Bullet, Bullet())
}

/** Spawns an enemy just above the top edge of the arena at a random x, drifting down. */
fun World.spawnEnemy(art: Art, random: Random) {
    val margin = Tuning.ENEMY_SWAY_AMPLITUDE + Tuning.SHIP_SIZE / 2
    val centerX = random.nextFloat(margin, Tuning.ARENA_WIDTH - margin)
    val enemy = create()
    add(enemy, Transform, Transform(centerX, -Tuning.SHIP_SIZE / 2))
    add(enemy, Sprite, Sprite(art.enemy, Tuning.SHIP_SIZE, Tuning.SHIP_SIZE, layer = Tuning.LAYER_SHIPS))
    add(enemy, Velocity, Velocity(0f, random.nextFloat(Tuning.ENEMY_MIN_SPEED, Tuning.ENEMY_MAX_SPEED)))
    val phase = random.nextFloat(0f, 2f * PI.toFloat())
    add(enemy, Enemy, Enemy(centerX, phase, random.nextFloat(Tuning.ENEMY_SWAY_MIN_RATE, Tuning.ENEMY_SWAY_MAX_RATE)))
}

/** Plays one explosion at ([x], [y]), above ships and bullets. */
fun World.spawnExplosion(art: Art, x: Float, y: Float) {
    val explosion = create()
    add(explosion, Transform, Transform(x, y))
    val animation = SpriteAnimation(art.explosion, Tuning.EXPLOSION_FRAME, AnimationMode.ONCE)
    add(explosion, Sprite, Sprite(animation.currentFrame, Tuning.EXPLOSION_SIZE, Tuning.EXPLOSION_SIZE, layer = Tuning.LAYER_EFFECTS))
    add(explosion, SpriteAnimation, animation)
    add(explosion, Explosion, Explosion())
}

/** Returns a random float in [from] until [until]. */
private fun Random.nextFloat(from: Float, until: Float): Float = from + nextFloat() * (until - from)
