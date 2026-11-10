package klyph.shooter

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.collision.Overlap
import klyph.collision.contains
import klyph.collision.otherThan
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.math.Rect
import klyph.math.Vec2
import klyph.math.angle
import klyph.math.degrees
import klyph.particle.ParticleEmitter
import klyph.platform.Color
import klyph.render.Sprite
import klyph.render.follow
import klyph.render.lookAt
import klyph.render.visibleHeight
import klyph.render.visibleWidth
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

// The game's systems. GameScenes adds them to its scenes in the order they appear here. Spawns
// and destroys go through app.commands, which the engine applies after each system, so queries
// can queue them freely. The engine destroys bullets through their Lifetime, explosions when
// their animation ends and spark bursts once their particles are gone.

// STARTUP

/**
 * Loads the art and sounds, shares the [Game], keeps the camera inside the arena and fills the
 * sky. The stars are spawned outside any scene, so they stay behind every screen for the whole
 * process.
 */
fun startGame(app: App, isAutopilot: Boolean) {
    val game = Game(Art.load(app.assets), Sounds.load(app.assets), isAutopilot)
    app.resources.insert(game)
    app.camera.bounds = Rect(0f, 0f, Tuning.ARENA_WIDTH, Tuning.ARENA_HEIGHT)
    app.commands.spawnStars(game.art, game.random)
    showArena(app)
}

/** Points the camera at the middle of the arena. */
fun showArena(app: App) {
    app.camera.lookAt(Tuning.ARENA_WIDTH / 2, Tuning.ARENA_HEIGHT / 2)
}

// Play scene: entering

/**
 * Starts a game: spawns the ship and its exhaust, which belong to the play scene, shares a new
 * [Round] and [Wave] and starts wave 1 at once, centers the camera and makes sure the music plays.
 */
fun startRound(app: App) {
    val game = app.game
    val player = app.commands.spawnPlayer(game.art)
    val thruster = app.commands.spawnThruster(game.art, player)
    app.resources.insert(Round(game, player, thruster))
    app.resources.insert(Wave(game.isAutopilot).apply { startNext(delay = 0f) })
    game.controls.release()
    showArena(app)
    game.startMusic(app)
}

// FIXED_UPDATE

/** Respawns, steers, turns and fires the player's ship; once the last life's wreck has cleared, ends the round. */
fun Round.updatePlayer(app: App) {
    val dt = app.time.deltaSeconds
    val ship = app.world.get(player, Player)
    if (!ship.isAlive) {
        ship.respawnTimer -= dt
        if (ship.isAlive) {
            if (lives > 0) respawn(app, ship) else end(ship)
        }
        return
    }
    val transform = app.world.get(player, Transform)
    ship.invulnerableTimer = (ship.invulnerableTimer - dt).coerceAtLeast(0f)
    steer(transform, dt)
    fire(app, ship, transform, dt)
}

private fun Round.steer(transform: Transform, dt: Float) {
    val controls = game.controls
    val half = Tuning.SHIP_SIZE / 2
    transform.x = (transform.x + controls.moveX * Tuning.PLAYER_SPEED * dt).coerceIn(half, Tuning.ARENA_WIDTH - half)
    transform.y = (transform.y + controls.moveY * Tuning.PLAYER_SPEED * dt).coerceIn(half, Tuning.ARENA_HEIGHT - half)
    if (!controls.heading.isNaN()) transform.rotation = controls.heading
}

/**
 * Fires along the ship's heading every [Tuning.FIRE_INTERVAL] while the trigger is held, carrying
 * the remainder between steps, with a shot sound at a slightly random pitch for each bullet.
 */
private fun Round.fire(app: App, ship: Player, transform: Transform, dt: Float) {
    ship.fireCooldown -= dt
    if (!game.controls.isFiring) {
        ship.fireCooldown = ship.fireCooldown.coerceAtLeast(0f)
        return
    }
    if (ship.fireCooldown > 0f) return
    // Rotation 0 faces up, a quarter turn back from +x, where Vec2 angles start.
    val heading = transform.rotation
    val nose = Vec2(transform.x, transform.y) + Vec2.fromAngle(heading - 90f.degrees, Tuning.SHIP_SIZE / 2)
    while (ship.fireCooldown <= 0f) {
        app.commands.spawnBullet(game.art, nose, heading)
        val pitch = Tuning.SHOT_MIN_PITCH + game.soundRandom.nextFloat() * (Tuning.SHOT_MAX_PITCH - Tuning.SHOT_MIN_PITCH)
        app.audio.play(game.sounds.shot, volume = Tuning.SHOT_VOLUME, pitch = pitch)
        ship.fireCooldown += Tuning.FIRE_INTERVAL
    }
}

/** Brings the ship back, invulnerable for a while. */
private fun Round.respawn(app: App, ship: Player) {
    ship.respawnTimer = 0f
    ship.invulnerableTimer = Tuning.INVULNERABLE_TIME
    ship.fireCooldown = 0f
    app.world.get(player, Sprite).isVisible = true
}

/**
 * Keeps the ship down for good, records the score and marks the round over for the play scene.
 * Bullets still in flight after the last life is lost can score until here.
 */
private fun Round.end(ship: Player) {
    ship.respawnTimer = Float.POSITIVE_INFINITY
    game.lastScore = score
    game.bestScore = maxOf(game.bestScore, score)
    isOver = true
    println("Game over. Final score $score.")
}

/**
 * Sends the wave's enemies every [Tuning.ENEMY_SPAWN_INTERVAL] while fewer than the cap are alive,
 * a share of them divers, or the boss alone on a boss wave, and starts the next wave once every
 * enemy of this one is gone.
 */
fun Round.spawnEnemies(app: App) {
    val wave = app.wave
    if (wave.remaining == 0) wave.startNext(delay = Tuning.WAVE_BREAK)
    wave.spawnTimer -= app.time.deltaSeconds
    while (wave.toSpawn > 0 && wave.spawnTimer <= 0f) {
        wave.spawnTimer += Tuning.ENEMY_SPAWN_INTERVAL
        if (app.world.count(Enemy) >= Tuning.ENEMY_MAX_ALIVE) continue
        wave.toSpawn--
        val random = game.random
        when {
            wave.isBoss -> app.commands.spawnBoss(game.art)
            random.nextFloat() < wave.diverShare -> {
                val ship = app.world.get(player, Transform)
                val viewTop = app.camera.y - app.camera.visibleHeight / 2
                app.commands.spawnDiver(game.art, random, Vec2(ship.x, ship.y), viewTop)
            }
            else -> app.commands.spawnDrifter(game.art, random)
        }
    }
}

/** Steers the divers along their curves; the engine moves them by their [Velocity] at the end of the step. */
fun steerDivers(app: App) {
    val dt = app.time.deltaSeconds
    app.world.forEach(Dive, Velocity, Transform) { _, dive, velocity, transform ->
        if (dive.progress < 1f) {
            dive.progress = (dive.progress + dt / dive.duration).coerceAtMost(1f)
            // The derivative of the curve, per second: where the diver heads at this point of it.
            val t = dive.progress
            velocity.dx = 2f * ((1f - t) * (dive.controlX - dive.startX) + t * (dive.targetX - dive.controlX)) / dive.duration
            velocity.dy = 2f * ((1f - t) * (dive.controlY - dive.startY) + t * (dive.targetY - dive.controlY)) / dive.duration
        }
        // The art points down, a quarter turn on from +x, where Vec2 angles start.
        transform.rotation = Vec2(velocity.dx, velocity.dy).angle - 90f.degrees
    }
}

/** Sways and banks the drifters; the engine carries them down by their [Velocity]. */
fun swayDrifters(app: App) {
    val dt = app.time.deltaSeconds
    app.world.forEach(Sway, Transform) { _, sway, transform ->
        sway.phase += sway.rate * dt
        transform.x = sway.centerX + Tuning.ENEMY_SWAY_AMPLITUDE * sin(sway.phase)
        // The enemy points down, so tipping its nose the way it sways is a counter-clockwise turn.
        transform.rotation = -Tuning.ENEMY_BANK * cos(sway.phase)
    }
}

/** Destroys anything moving that has left the arena; an enemy that got away counts as gone from its wave. */
fun Round.removeStrays(app: App) {
    val margin = Tuning.SHIP_SIZE
    val world = app.world
    val commands = app.commands
    world.forEach(Velocity, Transform) { entity, _, transform ->
        val isOutside = transform.x < -margin || transform.x > Tuning.ARENA_WIDTH + margin ||
            transform.y < -margin || transform.y > Tuning.ARENA_HEIGHT + margin
        if (!isOutside) return@forEach
        val enemy = world.getOrNull(entity, Enemy)
        if (enemy != null && !enemy.isHit) {
            enemy.isHit = true
            app.wave.remaining--
        }
        commands.destroy(entity)
    }
}

// UPDATE

/**
 * Sweeps the boss side to side at the top of the arena once it has come down, around a middle that
 * drifts after the ship, fires a fan of bullets down every [Tuning.BOSS_FIRE_INTERVAL] and shows
 * its untinted art for a moment after each hit.
 */
fun Round.updateBoss(app: App) {
    val dt = app.time.deltaSeconds
    val shipX = app.world.get(player, Transform).x
    val edge = Tuning.BOSS_SWEEP + Tuning.BOSS_SIZE / 2
    app.world.forEach(Boss, Transform, Sprite) { _, boss, transform, sprite ->
        boss.centerX = (boss.centerX + (shipX - boss.centerX) * (1f - exp(-Tuning.BOSS_TRACK_RATE * dt)))
            .coerceIn(edge, Tuning.ARENA_WIDTH - edge)
        boss.flashTimer -= dt
        // The flash shows the art untinted.
        sprite.color = if (boss.flashTimer > 0f) Color.WHITE else Tuning.BOSS_TINT
        // On the way down the sweep stands still, so the boss drifts straight in after the ship.
        val isComingDown = transform.y < Tuning.BOSS_Y
        if (isComingDown) {
            transform.y = (transform.y + Tuning.BOSS_ENTRY_SPEED * dt).coerceAtMost(Tuning.BOSS_Y)
        } else {
            boss.phase += Tuning.BOSS_SWEEP_RATE * dt
        }
        transform.x = boss.centerX + Tuning.BOSS_SWEEP * sin(boss.phase)
        if (isComingDown) return@forEach
        boss.fireTimer -= dt
        if (boss.fireTimer > 0f) return@forEach
        boss.fireTimer += Tuning.BOSS_FIRE_INTERVAL
        val muzzleY = transform.y + Tuning.BOSS_SIZE * 0.4f
        for (shot in 0 until Tuning.BOSS_VOLLEY) {
            val heading = (shot - (Tuning.BOSS_VOLLEY - 1) / 2f) * Tuning.BOSS_VOLLEY_SPREAD
            app.commands.spawnEnemyBullet(game.art, transform.x, muzzleY, heading)
        }
    }
}

/**
 * Resolves the overlaps the engine found this frame: a bullet damages an enemy, which is wrecked
 * and scores once its hit points are gone, and an enemy or an enemy bullet that reaches the ship
 * blows it up unless the ship is down or invulnerable. Each wreck leaves an explosion.
 */
fun Round.resolveOverlaps(app: App) {
    val world = app.world
    var isPlayerHit = false
    app.events.forEach<Overlap> { overlap ->
        if (player in overlap) {
            if (strike(app, overlap.otherThan(player))) isPlayerHit = true
        } else {
            // Every other pair the layers allow is a bullet and an enemy.
            val bullet = if (world.has(overlap.a, Bullet)) overlap.a else overlap.b
            shoot(app, bullet, overlap.otherThan(bullet))
        }
    }
    if (isPlayerHit) destroyPlayer(app)
}

/**
 * Applies [bullet] hitting [target], an enemy, unless either is already spent: takes a hit point
 * off it and, when none are left, wrecks it and scores; a boss that survives flashes.
 */
private fun Round.shoot(app: App, bullet: Entity, target: Entity) {
    val world = app.world
    val shot = world.getOrNull(bullet, Bullet) ?: return
    val enemy = world.getOrNull(target, Enemy) ?: return
    if (shot.isSpent || enemy.isHit) return
    shot.isSpent = true
    app.commands.destroy(bullet)
    enemy.hitPoints--
    if (enemy.hitPoints > 0) {
        world.getOrNull(target, Boss)?.flashTimer = Tuning.BOSS_FLASH_TIME
        return
    }
    wreck(app, target, enemy)
    score += enemy.kind.score
    game.kills++
}

/**
 * Applies [other], an enemy or an enemy bullet, reaching the ship when it can be hurt: an enemy
 * other than the boss is wrecked with it, a bullet is used up. Returns `true` when the ship is
 * hit, so the caller destroys it once however many things touched it.
 */
private fun Round.strike(app: App, other: Entity): Boolean {
    val world = app.world
    val ship = world.get(player, Player)
    if (!ship.isAlive || ship.invulnerableTimer > 0f) return false
    if (world.has(other, EnemyBullet)) {
        app.commands.destroy(other)
        return true
    }
    val enemy = world.getOrNull(other, Enemy) ?: return false
    if (enemy.isHit) return false
    // The boss shrugs off a ram; anything smaller goes down with the ship.
    if (enemy.kind != EnemyKind.BOSS) wreck(app, other, enemy)
    return true
}

/**
 * Marks [enemy] hit, so nothing else counts it this frame, queues its explosion and removal, counts
 * it gone from its wave and plays the explosion panned by where the enemy is across the view.
 */
private fun Round.wreck(app: App, entity: Entity, enemy: Enemy) {
    val transform = app.world.get(entity, Transform)
    enemy.isHit = true
    app.wave.remaining--
    app.commands.spawnExplosion(game.art, transform.x, transform.y)
    if (enemy.kind == EnemyKind.BOSS) {
        // A blast at each wing tip as well, so the boss goes out bigger than a ship.
        val reach = Tuning.BOSS_SIZE / 3
        app.commands.spawnExplosion(game.art, transform.x - reach, transform.y)
        app.commands.spawnExplosion(game.art, transform.x + reach, transform.y)
    }
    app.commands.destroy(entity)
    app.audio.play(game.sounds.explosion, pan = panOf(app, transform.x))
}

/** Returns the pan, from -1 to 1, of a sound at world [x], by where it is across the view. */
private fun panOf(app: App, x: Float): Float {
    val camera = app.camera
    return ((x - camera.x) / (camera.visibleWidth / 2)).coerceIn(-1f, 1f)
}

/**
 * Blows up the ship, hides it and parks it at the arena's center facing up, where it respawns.
 * Moving it while it is hidden keeps the interpolated respawn from sweeping across the arena.
 * Plays the hit sting, or on the last life the game-over tone while the music fades out.
 */
private fun Round.destroyPlayer(app: App) {
    val world = app.world
    val ship = world.get(player, Player)
    val transform = world.get(player, Transform)
    app.commands.spawnExplosion(game.art, transform.x, transform.y)
    world.get(player, Sprite).isVisible = false
    transform.x = Tuning.ARENA_WIDTH / 2
    transform.y = Tuning.ARENA_HEIGHT / 2
    transform.rotation = 0f
    ship.respawnTimer = Tuning.RESPAWN_DELAY
    lives--
    game.deaths++
    if (lives == 0) {
        app.audio.play(game.sounds.gameOver)
        game.stopMusic(app)
    } else {
        app.audio.play(game.sounds.hit)
    }
}

/** Blinks the player's ship while enemies pass through it. A ship waiting to respawn stays hidden. */
fun Round.blinkPlayer(app: App) {
    val ship = app.world.get(player, Player)
    if (!ship.isAlive) return
    val isBlinkedOut = ship.invulnerableTimer > 0f && (ship.invulnerableTimer * Tuning.BLINK_RATE).toInt() % 2 == 0
    app.world.get(player, Sprite).isVisible = !isBlinkedOut
}

/** Stops the exhaust while the ship is down; its local direction turns it with the ship. */
fun Round.runThruster(app: App) {
    val emitter = app.world.get(thruster, ParticleEmitter)
    emitter.rate = if (app.world.get(player, Player).isAlive) Tuning.THRUSTER_RATE else 0f
}

/** Counts down the "Wave n" banner. */
fun Wave.runBanner(app: App) {
    bannerTimer = (bannerTimer - app.time.deltaSeconds).coerceAtLeast(0f)
}

// LATE_UPDATE

/** Eases the camera toward the player; the camera's bounds keep the arena's edges out of view. */
fun Round.followCamera(app: App) {
    val ship = app.world.get(player, Transform)
    app.camera.follow(ship.x, ship.y, smoothing = Tuning.CAMERA_FOLLOW_RATE, delta = app.time.delta)
}
