package klyph.shooter

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.entity.Commands
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.render.Camera
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import klyph.render.lookAt
import klyph.render.visibleHeight
import klyph.render.visibleWidth
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

// The game's systems, in the order Main registers them. Spawns and destroys go through
// app.commands, which the engine applies after each system, so queries can queue them freely.

// STARTUP

/** Loads the art, creates the player, fills the sky, points the camera at the ship and shares the [Game]. */
fun startGame(app: App, isAutopilot: Boolean) {
    val art = Art.load(app.assets)
    val game = Game(art, isAutopilot, app.commands.spawnPlayer(art))
    app.commands.spawnStars(art, game.random)
    app.resources.insert(game)
    app.camera.lookAt(Tuning.ARENA_WIDTH / 2, Tuning.ARENA_HEIGHT / 2)
    clampToArena(app.camera)
    game.printScore()
}

// FIXED_UPDATE

/** Respawns, steers, turns and fires the player's ship. */
fun Game.updatePlayer(app: App) {
    val dt = app.time.deltaSeconds
    val ship = app.world.get(player, Player)
    if (!ship.isAlive) {
        ship.respawnTimer -= dt
        if (ship.isAlive) respawn(app.world, ship)
        return
    }
    val transform = app.world.get(player, Transform)
    ship.invulnerableTimer = (ship.invulnerableTimer - dt).coerceAtLeast(0f)
    steer(transform, dt)
    fire(app.commands, ship, transform, dt)
}

private fun Game.steer(transform: Transform, dt: Float) {
    val half = Tuning.SHIP_SIZE / 2
    transform.x = (transform.x + controls.moveX * Tuning.PLAYER_SPEED * dt).coerceIn(half, Tuning.ARENA_WIDTH - half)
    transform.y = (transform.y + controls.moveY * Tuning.PLAYER_SPEED * dt).coerceIn(half, Tuning.ARENA_HEIGHT - half)
    if (!controls.heading.isNaN()) transform.rotation = controls.heading
}

/** Fires along the ship's heading every [Tuning.FIRE_INTERVAL] while the trigger is held, carrying the remainder between steps. */
private fun Game.fire(commands: Commands, ship: Player, transform: Transform, dt: Float) {
    ship.fireCooldown -= dt
    if (!controls.isFiring) {
        ship.fireCooldown = ship.fireCooldown.coerceAtLeast(0f)
        return
    }
    if (ship.fireCooldown > 0f) return
    // Rotation 0 faces up, so the nose is at (sin, -cos) of the heading.
    val heading = transform.rotation
    val noseX = transform.x + Tuning.SHIP_SIZE / 2 * sin(heading)
    val noseY = transform.y - Tuning.SHIP_SIZE / 2 * cos(heading)
    while (ship.fireCooldown <= 0f) {
        commands.spawnBullet(art, noseX, noseY, heading)
        ship.fireCooldown += Tuning.FIRE_INTERVAL
    }
}

private fun Game.respawn(world: World, ship: Player) {
    ship.respawnTimer = 0f
    ship.invulnerableTimer = Tuning.INVULNERABLE_TIME
    ship.fireCooldown = 0f
    world.get(player, Sprite).isVisible = true
    if (lives == 0) {
        lives = Tuning.PLAYER_LIVES
        score = 0
        println("New game.")
        printScore()
    }
}

/** Spawns an enemy every [Tuning.ENEMY_SPAWN_INTERVAL] while fewer than the cap are alive. */
fun Game.spawnEnemies(app: App) {
    spawnTimer -= app.time.deltaSeconds
    while (spawnTimer <= 0f) {
        spawnTimer += Tuning.ENEMY_SPAWN_INTERVAL
        if (app.world.count(Enemy) < Tuning.ENEMY_MAX_ALIVE) app.commands.spawnEnemy(art, random)
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

/** Resolves bullet hits and enemies ramming the player, leaving an explosion for each wreck. */
fun Game.collide(app: App) {
    val world = app.world
    val commands = app.commands
    val scoreBefore = score
    world.forEach(Bullet, Transform, Sprite) { bulletEntity, bullet, bulletTransform, bulletSprite ->
        val bulletRadius = radiusOf(bulletSprite, scale = 1f)
        world.forEach(Enemy, Transform, Sprite) { enemyEntity, enemy, enemyTransform, enemySprite ->
            if (bullet.isSpent || enemy.isHit) return@forEach
            if (overlaps(bulletTransform, bulletRadius, enemyTransform, radiusOf(enemySprite, Tuning.SHIP_HITBOX_SCALE))) {
                bullet.isSpent = true
                commands.destroy(bulletEntity)
                wreck(commands, enemyEntity, enemy, enemyTransform)
                score += Tuning.ENEMY_SCORE
                kills++
            }
        }
    }
    if (isRammed(world, commands)) destroyPlayer(world, commands)
    else if (score != scoreBefore) printScore()
}

/** Returns `true` if an enemy touches the player's ship, and wrecks every such enemy. */
private fun Game.isRammed(world: World, commands: Commands): Boolean {
    val ship = world.get(player, Player)
    if (!ship.isAlive || ship.invulnerableTimer > 0f) return false
    val shipTransform = world.get(player, Transform)
    val shipRadius = radiusOf(world.get(player, Sprite), Tuning.SHIP_HITBOX_SCALE)
    var isRammed = false
    world.forEach(Enemy, Transform, Sprite) { enemyEntity, enemy, enemyTransform, enemySprite ->
        if (!enemy.isHit && overlaps(shipTransform, shipRadius, enemyTransform, radiusOf(enemySprite, Tuning.SHIP_HITBOX_SCALE))) {
            wreck(commands, enemyEntity, enemy, enemyTransform)
            isRammed = true
        }
    }
    return isRammed
}

/** Marks [enemy] hit, so nothing else counts it this step, and queues its explosion and removal. */
private fun Game.wreck(commands: Commands, entity: Entity, enemy: Enemy, transform: Transform) {
    enemy.isHit = true
    commands.spawnExplosion(art, transform.x, transform.y)
    commands.destroy(entity)
}

/**
 * Blows up the ship, hides it and parks it at the arena's center facing up, where it respawns.
 * Moving it while it is hidden keeps the interpolated respawn from sweeping across the arena.
 */
private fun Game.destroyPlayer(world: World, commands: Commands) {
    val ship = world.get(player, Player)
    val transform = world.get(player, Transform)
    commands.spawnExplosion(art, transform.x, transform.y)
    world.get(player, Sprite).isVisible = false
    transform.x = Tuning.ARENA_WIDTH / 2
    transform.y = Tuning.ARENA_HEIGHT / 2
    transform.rotation = 0f
    ship.respawnTimer = Tuning.RESPAWN_DELAY
    lives--
    deaths++
    printScore()
    if (lives == 0) println("Game over. Final score $score.")
}

/** Destroys bullets whose time is up and anything that has left the arena. */
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

/** Removes explosions whose animation has played to the end. */
fun removeFinishedExplosions(app: App) {
    val commands = app.commands
    app.world.forEach(Explosion, SpriteAnimation) { entity, _, animation ->
        if (animation.animation.isFinished) commands.destroy(entity)
    }
}

/** Blinks the player's ship while enemies pass through it. A ship waiting to respawn stays hidden. */
fun Game.blinkPlayer(app: App) {
    val ship = app.world.get(player, Player)
    if (!ship.isAlive) return
    val isBlinkedOut = ship.invulnerableTimer > 0f && (ship.invulnerableTimer * Tuning.BLINK_RATE).toInt() % 2 == 0
    app.world.get(player, Sprite).isVisible = !isBlinkedOut
}

// LATE_UPDATE

/** Eases the camera toward the player and keeps the arena's edges out of view. */
fun Game.followCamera(app: App) {
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

// Helpers

/** Returns the share of the remaining distance to cover this step when easing at [rate] per second. */
private fun approach(rate: Float, dt: Float): Float = 1f - exp(-rate * dt)

private fun radiusOf(sprite: Sprite, scale: Float): Float = 0.5f * minOf(sprite.width, sprite.height) * scale

private fun overlaps(a: Transform, radiusA: Float, b: Transform, radiusB: Float): Boolean {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val reach = radiusA + radiusB
    return dx * dx + dy * dy < reach * reach
}
