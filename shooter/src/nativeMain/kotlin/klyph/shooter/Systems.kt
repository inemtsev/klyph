package klyph.shooter

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.entity.Transform
import klyph.entity.World
import klyph.platform.Color
import klyph.render.Camera
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import klyph.render.lookAt
import klyph.render.visibleHeight
import klyph.render.visibleWidth
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

// The game's systems, in the order Main registers them. Queries never create or destroy
// entities while they run: they collect them in Game.doomed and Game.wrecked, and the system
// applies the changes once its queries are over.

// STARTUP

/** Fills the sky, creates the player and points the camera at it. */
fun Game.start(app: App) {
    app.world.spawnStars(art, random)
    player = app.world.spawnPlayer(art)
    val ship = app.world.get(player, Transform)
    fitZoom(app.camera)
    app.camera.lookAt(ship.x, ship.y)
    clampToArena(app.camera)
    printScore()
}

// FIXED_UPDATE

/** Respawns, steers and fires the player's ship. */
fun Game.updatePlayer(app: App) {
    val dt = app.time.deltaSeconds
    val ship = app.world.get(player, Player)
    val transform = app.world.get(player, Transform)
    if (!ship.isAlive) {
        ship.respawnTimer -= dt
        if (ship.isAlive) respawn(app.world, ship, transform)
        return
    }
    ship.invulnerableTimer = (ship.invulnerableTimer - dt).coerceAtLeast(0f)
    steer(ship, transform, dt)
    fire(app.world, ship, transform, dt)
}

private fun Game.steer(ship: Player, transform: Transform, dt: Float) {
    val half = Tuning.SHIP_SIZE / 2
    transform.x = (transform.x + controls.moveX * Tuning.PLAYER_SPEED * dt).coerceIn(half, Tuning.ARENA_WIDTH - half)
    transform.y = (transform.y + controls.moveY * Tuning.PLAYER_SPEED * dt).coerceIn(half, Tuning.ARENA_HEIGHT - half)
    // Positive rotation turns clockwise on screen, so moving right tips the nose right.
    ship.bank += (controls.moveX * Tuning.PLAYER_BANK - ship.bank) * approach(Tuning.PLAYER_BANK_RATE, dt)
    transform.rotation = ship.bank
}

/** Fires every [Tuning.FIRE_INTERVAL] while the trigger is held, carrying the remainder between steps. */
private fun Game.fire(world: World, ship: Player, transform: Transform, dt: Float) {
    ship.fireCooldown -= dt
    if (!controls.isFiring) {
        ship.fireCooldown = ship.fireCooldown.coerceAtLeast(0f)
        return
    }
    while (ship.fireCooldown <= 0f) {
        world.spawnBullet(art, transform.x, transform.y - Tuning.SHIP_SIZE / 2)
        ship.fireCooldown += Tuning.FIRE_INTERVAL
    }
}

private fun Game.respawn(world: World, ship: Player, transform: Transform) {
    ship.respawnTimer = 0f
    ship.invulnerableTimer = Tuning.INVULNERABLE_TIME
    ship.bank = 0f
    ship.fireCooldown = 0f
    transform.x = Tuning.ARENA_WIDTH / 2
    transform.y = Tuning.ARENA_HEIGHT / 2
    transform.rotation = 0f
    world.add(player, Sprite, ship.sprite)
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
        if (app.world.count(Enemy) < Tuning.ENEMY_MAX_ALIVE) app.world.spawnEnemy(art, random)
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
    val scoreBefore = score
    world.forEach(Bullet, Transform, Sprite) { bulletEntity, bullet, bulletTransform, bulletSprite ->
        val bulletRadius = radiusOf(bulletSprite, scale = 1f)
        world.forEach(Enemy, Transform, Sprite) { enemyEntity, enemy, enemyTransform, enemySprite ->
            if (bullet.isSpent || enemy.isHit) return@forEach
            if (overlaps(bulletTransform, bulletRadius, enemyTransform, radiusOf(enemySprite, Tuning.SHIP_HITBOX_SCALE))) {
                bullet.isSpent = true
                enemy.isHit = true
                doomed.add(bulletEntity)
                wrecked.add(enemyEntity)
                score += Tuning.ENEMY_SCORE
                kills++
            }
        }
    }
    if (isRammed(world)) destroyPlayer(world)
    else if (score != scoreBefore) printScore()
    explodeWrecks(world)
    destroyDoomed(world)
}

/** Returns `true` if an enemy touches the player's ship, and marks every such enemy wrecked. */
private fun Game.isRammed(world: World): Boolean {
    val ship = world.get(player, Player)
    if (!ship.isAlive || ship.invulnerableTimer > 0f) return false
    val shipTransform = world.get(player, Transform)
    val shipRadius = radiusOf(ship.sprite, Tuning.SHIP_HITBOX_SCALE)
    var isRammed = false
    world.forEach(Enemy, Transform, Sprite) { enemyEntity, enemy, enemyTransform, enemySprite ->
        if (!enemy.isHit && overlaps(shipTransform, shipRadius, enemyTransform, radiusOf(enemySprite, Tuning.SHIP_HITBOX_SCALE))) {
            enemy.isHit = true
            wrecked.add(enemyEntity)
            isRammed = true
        }
    }
    return isRammed
}

private fun Game.destroyPlayer(world: World) {
    val ship = world.get(player, Player)
    val transform = world.get(player, Transform)
    world.spawnExplosion(art, transform.x, transform.y)
    world.remove(player, Sprite)
    ship.respawnTimer = Tuning.RESPAWN_DELAY
    lives--
    deaths++
    printScore()
    if (lives == 0) println("Game over. Final score $score.")
}

private fun Game.explodeWrecks(world: World) {
    for (i in 0 until wrecked.size) {
        val transform = world.get(wrecked[i], Transform)
        world.spawnExplosion(art, transform.x, transform.y)
        world.destroy(wrecked[i])
    }
    wrecked.clear()
}

/** Destroys bullets whose time is up and anything that has left the arena. */
fun Game.expire(app: App) {
    val dt = app.time.deltaSeconds
    val margin = Tuning.SHIP_SIZE
    app.world.forEach(Lifetime, Transform) { entity, lifetime, _ ->
        lifetime.remaining -= dt
        if (lifetime.remaining <= 0f) doomed.add(entity)
    }
    app.world.forEach(Velocity, Transform) { entity, _, transform ->
        val isOutside = transform.x < -margin || transform.x > Tuning.ARENA_WIDTH + margin ||
            transform.y < -margin || transform.y > Tuning.ARENA_HEIGHT + margin
        if (isOutside) doomed.add(entity)
    }
    destroyDoomed(app.world)
}

private fun Game.destroyDoomed(world: World) {
    // destroy ignores entities that are already gone, so an entry listed twice is harmless.
    for (i in 0 until doomed.size) world.destroy(doomed[i])
    doomed.clear()
}

// UPDATE

/** Removes explosions whose animation has played to the end. */
fun Game.removeFinishedExplosions(app: App) {
    app.world.forEach(Explosion, SpriteAnimation) { entity, _, animation ->
        if (animation.animation.isFinished) doomed.add(entity)
    }
    destroyDoomed(app.world)
}

/** Blinks the player's ship while enemies pass through it. */
fun Game.blinkPlayer(app: App) {
    val ship = app.world.get(player, Player)
    val isDimmed = ship.invulnerableTimer > 0f && (ship.invulnerableTimer * Tuning.BLINK_RATE).toInt() % 2 == 0
    ship.sprite.color = if (isDimmed) DIMMED else Color.WHITE
}

private val DIMMED = Color(1f, 1f, 1f, 0.3f)

// LATE_UPDATE

/** Eases the camera toward the player and keeps the arena's edges out of view. */
fun Game.followCamera(app: App) {
    val camera = app.camera
    val ship = app.world.get(player, Transform)
    val t = approach(Tuning.CAMERA_FOLLOW_RATE, app.time.deltaSeconds)
    fitZoom(camera)
    camera.lookAt(camera.x + (ship.x - camera.x) * t, camera.y + (ship.y - camera.y) * t)
    clampToArena(camera)
}

/** Zooms so the view is [Tuning.VIEW_HEIGHT] world units tall on any window and any pixel density. */
private fun fitZoom(camera: Camera) {
    camera.zoom = camera.viewportHeight / Tuning.VIEW_HEIGHT
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
