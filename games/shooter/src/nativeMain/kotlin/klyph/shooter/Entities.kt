package klyph.shooter

import klyph.collision.Collider
import klyph.collision.Shape
import klyph.entity.Commands
import klyph.entity.Entity
import klyph.entity.Interpolated
import klyph.entity.Lifetime
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.math.Vec2
import klyph.math.degrees
import klyph.math.distanceTo
import klyph.particle.EmitterSpace
import klyph.particle.ParticleEmitter
import klyph.particle.spawnBurst
import klyph.platform.Color
import klyph.render.AnimationEnd
import klyph.render.AnimationMode
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import kotlin.random.Random

// Factories for every kind of entity in the game. They queue the entity on App.commands, so they
// are safe to call inside a World.forEach; the components attach after the running system.
// Everything moved in FIXED_UPDATE is Interpolated, so it is drawn smoothly between steps.
// Entities spawned while a scene's onEnter or systems run belong to that scene and are destroyed
// when it leaves the stack, so nothing here needs cleaning up by hand.

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
    add(Collider, Collider(Shape.Circle(Tuning.SHIP_RADIUS), layers = Layers.PLAYER, mask = Layers.ENEMY + Layers.ENEMY_BULLET))
    add(Player, Player())
    add(Interpolated, Interpolated)
}

/**
 * Creates the exhaust of [ship]: a child at its tail, so it follows the ship and turns with it,
 * whose emitter trails blue sparks. Its direction is local, so it points out of the tail whichever
 * way the ship faces.
 */
fun Commands.spawnThruster(art: Art, ship: Entity): Entity = spawn {
    val emitter = ParticleEmitter(art.particle, capacity = Tuning.THRUSTER_CAPACITY).apply {
        rate = Tuning.THRUSTER_RATE
        // The ship points up at rotation 0, so its tail is straight down, a quarter turn from +x.
        direction = 90f.degrees
        space = EmitterSpace.LOCAL
        spread = Tuning.THRUSTER_SPREAD.degrees
        speed = Tuning.THRUSTER_MIN_SPEED..Tuning.THRUSTER_MAX_SPEED
        lifetime = Tuning.THRUSTER_MIN_LIFETIME..Tuning.THRUSTER_MAX_LIFETIME
        startColor = Tuning.THRUSTER_START_COLOR
        endColor = Tuning.THRUSTER_END_COLOR
        startScale = Tuning.THRUSTER_START_SCALE
        endScale = Tuning.THRUSTER_END_SCALE
        layer = Tuning.LAYER_BULLETS
    }
    add(Transform, Transform(0f, Tuning.THRUSTER_OFFSET))
    add(ParticleEmitter, emitter)
    setParent(ship)
}

/** Fires a bullet from [nose] along [heading], in radians clockwise from straight up. */
fun Commands.spawnBullet(art: Art, nose: Vec2, heading: Float) {
    spawn {
        val velocity = Vec2.fromAngle(heading - 90f.degrees, Tuning.BULLET_SPEED)
        add(Transform, Transform(nose.x, nose.y, rotation = heading))
        add(Sprite, Sprite(art.bullet, Tuning.BULLET_WIDTH, Tuning.BULLET_HEIGHT, layer = Tuning.LAYER_BULLETS))
        add(Collider, Collider(Shape.Circle(Tuning.BULLET_RADIUS), layers = Layers.BULLET, mask = Layers.ENEMY))
        add(Velocity, Velocity(velocity.x, velocity.y))
        add(Lifetime, Lifetime(Tuning.BULLET_LIFETIME))
        add(Bullet, Bullet())
        add(Interpolated, Interpolated)
    }
}

/**
 * Fires a boss bullet from ([x], [y]) along [heading], in radians clockwise from straight down:
 * the player's bullet art turned round and tinted, which hurts the player.
 */
fun Commands.spawnEnemyBullet(art: Art, x: Float, y: Float, heading: Float) {
    spawn {
        val velocity = Vec2.fromAngle(heading + 90f.degrees, Tuning.ENEMY_BULLET_SPEED)
        add(Transform, Transform(x, y, rotation = heading + 180f.degrees))
        add(Sprite, Sprite(art.bullet, Tuning.BULLET_WIDTH, Tuning.BULLET_HEIGHT, Tuning.ENEMY_BULLET_TINT, layer = Tuning.LAYER_BULLETS))
        add(Collider, Collider(Shape.Circle(Tuning.BULLET_RADIUS), layers = Layers.ENEMY_BULLET, mask = Layers.PLAYER))
        add(Velocity, Velocity(velocity.x, velocity.y))
        add(Lifetime, Lifetime(Tuning.ENEMY_BULLET_LIFETIME))
        add(EnemyBullet, EnemyBullet)
        add(Interpolated, Interpolated)
    }
}

/** Spawns a drifter just above the top edge of the arena at a random x, drifting down. */
fun Commands.spawnDrifter(art: Art, random: Random) {
    spawn {
        val margin = Tuning.ENEMY_SWAY_AMPLITUDE + Tuning.SHIP_SIZE / 2
        val centerX = random.nextFloat(margin, Tuning.ARENA_WIDTH - margin)
        val phase = random.nextFloat(0f, 360f.degrees)
        add(Transform, Transform(centerX, -Tuning.SHIP_SIZE / 2))
        add(Sprite, Sprite(art.enemy, Tuning.SHIP_SIZE, Tuning.SHIP_SIZE, layer = Tuning.LAYER_SHIPS))
        add(Collider, enemyCollider(Tuning.SHIP_RADIUS))
        add(Velocity, Velocity(0f, random.nextFloat(Tuning.ENEMY_MIN_SPEED, Tuning.ENEMY_MAX_SPEED)))
        add(Sway, Sway(centerX, phase, random.nextFloat(Tuning.ENEMY_SWAY_MIN_RATE, Tuning.ENEMY_SWAY_MAX_RATE)))
        add(Enemy, Enemy(EnemyKind.DRIFTER))
        add(Interpolated, Interpolated)
    }
}

/**
 * Spawns a diver just above the top of the view ([viewTop]), off to one side of [ship], aimed at
 * where the ship is now. Its curve falls straight down at first and bends toward the ship late,
 * so it swoops through that point and carries on down and across.
 */
fun Commands.spawnDiver(art: Art, random: Random, ship: Vec2, viewTop: Float) {
    spawn {
        val side = if (random.nextBoolean()) 1f else -1f
        val half = Tuning.SHIP_SIZE / 2
        val start = Vec2(
            (ship.x + side * random.nextFloat(Tuning.DIVER_MIN_OFFSET, Tuning.DIVER_MAX_OFFSET)).coerceIn(half, Tuning.ARENA_WIDTH - half),
            viewTop - half,
        )
        val control = Vec2(start.x, start.y + (ship.y - start.y) * Tuning.DIVER_PULL)
        // A curve is a little longer than the straight line under it.
        val duration = 1.15f * start.distanceTo(ship) / Tuning.DIVER_SPEED
        add(Transform, Transform(start.x, start.y))
        add(Sprite, Sprite(art.diver, Tuning.SHIP_SIZE, Tuning.SHIP_SIZE, layer = Tuning.LAYER_SHIPS))
        add(Collider, enemyCollider(Tuning.SHIP_RADIUS))
        add(Velocity, Velocity())
        add(Dive, Dive(start.x, start.y, control.x, control.y, ship.x, ship.y, duration.coerceAtLeast(MIN_DIVE_SECONDS)))
        add(Enemy, Enemy(EnemyKind.DIVER))
        add(Interpolated, Interpolated)
    }
}

/**
 * Spawns the boss above the middle of the arena's top edge: the diver art three times as large and
 * tinted, which comes down to [Tuning.BOSS_Y] and then sweeps and fires. It moves in UPDATE and is
 * not interpolated, so its health bar, drawn from its transform, stays on it.
 */
fun Commands.spawnBoss(art: Art) {
    spawn {
        add(Transform, Transform(Tuning.ARENA_WIDTH / 2, -Tuning.BOSS_SIZE / 2))
        add(Sprite, Sprite(art.diver, Tuning.BOSS_SIZE, Tuning.BOSS_SIZE, Tuning.BOSS_TINT, layer = Tuning.LAYER_SHIPS))
        add(Collider, enemyCollider(Tuning.BOSS_RADIUS))
        add(Boss, Boss())
        add(Enemy, Enemy(EnemyKind.BOSS))
    }
}

/** Returns the collider of an enemy, which bullets and the player find. */
private fun enemyCollider(radius: Float): Collider =
    Collider(Shape.Circle(radius), layers = Layers.ENEMY, mask = Layers.PLAYER + Layers.BULLET)

/**
 * Plays one explosion at ([x], [y]), above ships and bullets: the animated flash, which goes once
 * it has played, and a burst of sparks on an entity of its own, which goes once they have faded.
 */
fun Commands.spawnExplosion(art: Art, x: Float, y: Float) {
    spawn {
        val animation = SpriteAnimation(art.explosion, Tuning.EXPLOSION_FRAME, AnimationMode.ONCE, AnimationEnd.DESTROY)
        add(Transform, Transform(x, y))
        add(Sprite, Sprite(animation.currentFrame, Tuning.EXPLOSION_SIZE, Tuning.EXPLOSION_SIZE, layer = Tuning.LAYER_EFFECTS))
        add(SpriteAnimation, animation)
    }
    spawnBurst(
        x, y,
        ParticleEmitter.burst(
            art.particle, Tuning.BURST_PARTICLES,
            speed = Tuning.BURST_MIN_SPEED..Tuning.BURST_MAX_SPEED, lifetime = Tuning.BURST_MIN_LIFETIME..Tuning.BURST_MAX_LIFETIME,
            startColor = Tuning.BURST_START_COLOR, endColor = Tuning.BURST_END_COLOR,
            startScale = Tuning.BURST_START_SCALE, endScale = Tuning.BURST_END_SCALE, layer = Tuning.LAYER_EFFECTS,
        ),
    )
}

/** A dive at the ship from right next to it still takes this long, so it is a curve and not a jump. */
private const val MIN_DIVE_SECONDS = 0.5f

/** Returns a random float in [from] until [until]. */
private fun Random.nextFloat(from: Float, until: Float): Float = from + nextFloat() * (until - from)
