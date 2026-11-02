package klyph.shooter

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.collision.Overlap
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.particle.ParticleEmitter
import klyph.render.Camera
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import klyph.render.lookAt
import klyph.render.visibleHeight
import klyph.render.visibleWidth
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

// The game's systems. GameScenes adds them to its scenes in the order they appear here. Spawns
// and destroys go through app.commands, which the engine applies after each system, so queries
// can queue them freely.

// STARTUP

/**
 * Loads the art and sounds, shares the [Game] and fills the sky. The stars are spawned outside any
 * scene, so they stay behind every screen for the whole process.
 */
fun startGame(app: App, isAutopilot: Boolean) {
    val game = Game(Art.load(app.assets), Sounds.load(app.assets), isAutopilot)
    app.resources.insert(game)
    app.commands.spawnStars(game.art, game.random)
    showArena(app)
}

/** Points the camera at the middle of the arena. */
fun showArena(app: App) {
    app.camera.lookAt(Tuning.ARENA_WIDTH / 2, Tuning.ARENA_HEIGHT / 2)
    clampToArena(app.camera)
}

// Play scene: entering

/**
 * Starts a game: spawns the ship and its exhaust, which belong to the play scene, shares a new
 * [Round], centers the camera and makes sure the music plays.
 */
fun startRound(app: App) {
    val game = app.game
    val player = app.commands.spawnPlayer(game.art)
    val thruster = app.commands.spawnThruster(game.art, player)
    app.resources.insert(Round(game, player, thruster))
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
    // Rotation 0 faces up, so the nose is at (sin, -cos) of the heading.
    val heading = transform.rotation
    val noseX = transform.x + Tuning.SHIP_SIZE / 2 * sin(heading)
    val noseY = transform.y - Tuning.SHIP_SIZE / 2 * cos(heading)
    while (ship.fireCooldown <= 0f) {
        app.commands.spawnBullet(game.art, noseX, noseY, heading)
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

/** Spawns an enemy every [Tuning.ENEMY_SPAWN_INTERVAL] while fewer than the cap are alive. */
fun Round.spawnEnemies(app: App) {
    spawnTimer -= app.time.deltaSeconds
    while (spawnTimer <= 0f) {
        spawnTimer += Tuning.ENEMY_SPAWN_INTERVAL
        if (app.world.count(Enemy) < Tuning.ENEMY_MAX_ALIVE) app.commands.spawnEnemy(game.art, game.random)
    }
}

/** Moves everything with a [Velocity], and sways and banks the enemies. */
fun moveBodies(app: App) {
    val dt = app.time.deltaSeconds
    app.world.forEach(Velocity, Transform) { _, velocity, transform ->
        transform.x += velocity.x * dt
        transform.y += velocity.y * dt
    }
    app.world.forEach(Enemy, Transform) { _, enemy, transform ->
        enemy.swayPhase += enemy.swayRate * dt
        transform.x = enemy.centerX + Tuning.ENEMY_SWAY_AMPLITUDE * sin(enemy.swayPhase)
        // The enemy points down, so tipping its nose the way it sways is a counter-clockwise turn.
        transform.rotation = -Tuning.ENEMY_BANK * cos(enemy.swayPhase)
    }
}

/** Destroys bullets and spent spark bursts whose time is up, and anything that has left the arena. */
fun expire(app: App) {
    val dt = app.time.deltaSeconds
    val margin = Tuning.SHIP_SIZE
    val commands = app.commands
    app.world.forEach(Lifetime) { entity, lifetime ->
        lifetime.remaining -= dt
        if (lifetime.remaining <= 0f) commands.destroy(entity)
    }
    app.world.forEach(Velocity, Transform) { entity, _, transform ->
        val isOutside = transform.x < -margin || transform.x > Tuning.ARENA_WIDTH + margin ||
            transform.y < -margin || transform.y > Tuning.ARENA_HEIGHT + margin
        // A bullet whose lifetime also ran out is queued twice; destroying a gone entity is skipped.
        if (isOutside) commands.destroy(entity)
    }
}

// UPDATE

/**
 * Resolves the overlaps the engine found this frame: a bullet and an enemy destroy each other and
 * score, and an enemy that rams the ship blows up with it unless the ship is down or invulnerable.
 * Each wreck leaves an explosion.
 */
fun Round.resolveOverlaps(app: App) {
    var isRammed = false
    app.events.forEach<Overlap> { overlap ->
        // The engine orders a pair by entity index, so try both ways round.
        if (hit(app, overlap.a, overlap.b) || hit(app, overlap.b, overlap.a)) isRammed = true
    }
    if (isRammed) destroyPlayer(app)
}

/**
 * Applies [first] hitting [second] if [second] is an enemy nothing has hit yet this frame: a
 * bullet wrecks it and scores, the ship wrecks it when it can be hurt. Returns `true` only when
 * the ship rammed it, so the caller destroys the ship once however many enemies it touched.
 */
private fun Round.hit(app: App, first: Entity, second: Entity): Boolean {
    val world = app.world
    val enemy = world.getOrNull(second, Enemy) ?: return false
    if (enemy.isHit) return false
    val bullet = world.getOrNull(first, Bullet)
    if (bullet != null) {
        if (bullet.isSpent) return false
        bullet.isSpent = true
        app.commands.destroy(first)
        wreck(app, second, enemy)
        score += Tuning.ENEMY_SCORE
        game.kills++
        return false
    }
    if (first != player) return false
    val ship = world.get(player, Player)
    if (!ship.isAlive || ship.invulnerableTimer > 0f) return false
    wreck(app, second, enemy)
    return true
}

/**
 * Marks [enemy] hit, so nothing else counts it this frame, queues its explosion and removal, and
 * plays the explosion panned by where the enemy is across the view.
 */
private fun Round.wreck(app: App, entity: Entity, enemy: Enemy) {
    val transform = app.world.get(entity, Transform)
    enemy.isHit = true
    app.commands.spawnExplosion(game.art, transform.x, transform.y)
    app.commands.destroy(entity)
    val camera = app.camera
    val pan = ((transform.x - camera.x) / (camera.visibleWidth / 2)).coerceIn(-1f, 1f)
    app.audio.play(game.sounds.explosion, pan = pan)
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

/** Removes explosions whose animation has played to the end. */
fun removeFinishedExplosions(app: App) {
    val commands = app.commands
    app.world.forEach(Explosion, SpriteAnimation) { entity, _, animation ->
        if (animation.animation.isFinished) commands.destroy(entity)
    }
}

/** Blinks the player's ship while enemies pass through it. A ship waiting to respawn stays hidden. */
fun Round.blinkPlayer(app: App) {
    val ship = app.world.get(player, Player)
    if (!ship.isAlive) return
    val isBlinkedOut = ship.invulnerableTimer > 0f && (ship.invulnerableTimer * Tuning.BLINK_RATE).toInt() % 2 == 0
    app.world.get(player, Sprite).isVisible = !isBlinkedOut
}

/**
 * Points the exhaust out of the ship's tail and stops it while the ship is down. The engine
 * advances emitters right after UPDATE, so the new direction applies to this frame's particles.
 */
fun Round.aimThruster(app: App) {
    val emitter = app.world.get(thruster, ParticleEmitter)
    emitter.rate = if (app.world.get(player, Player).isAlive) Tuning.THRUSTER_RATE else 0f
    emitter.direction = thrusterDirection(app.world.get(player, Transform).rotation)
}

// LATE_UPDATE

/** Eases the camera toward the player and keeps the arena's edges out of view. */
fun Round.followCamera(app: App) {
    val camera = app.camera
    val ship = app.world.get(player, Transform)
    val t = approach(Tuning.CAMERA_FOLLOW_RATE, app.time.deltaSeconds)
    camera.lookAt(camera.x + (ship.x - camera.x) * t, camera.y + (ship.y - camera.y) * t)
    clampToArena(camera)
}

private fun clampToArena(camera: Camera) {
    camera.x = clampView(camera.x, camera.visibleWidth, Tuning.ARENA_WIDTH)
    camera.y = clampView(camera.y, camera.visibleHeight, Tuning.ARENA_HEIGHT)
}

/** Returns [center] moved so a view [visible] wide stays inside 0..[arena], or the arena's middle if the view is wider. */
private fun clampView(center: Float, visible: Float, arena: Float): Float =
    if (visible >= arena) arena / 2 else center.coerceIn(visible / 2, arena - visible / 2)

/** Returns the share of the remaining distance to cover this step when easing at [rate] per second. */
private fun approach(rate: Float, dt: Float): Float = 1f - exp(-rate * dt)
