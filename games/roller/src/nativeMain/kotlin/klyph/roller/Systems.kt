package klyph.roller

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.KinematicBody
import klyph.collision.Overlap
import klyph.collision.otherThan
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.math.Rect
import klyph.platform.Color
import klyph.render.Sprite
import klyph.render.clampToBounds
import klyph.render.follow
import klyph.render.lookAt
import klyph.render.visibleWidth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// The game's systems. GameScenes adds them to its scenes in the order they appear here. Spawns
// and destroys go through app.commands, which the engine applies after each system, so queries
// can queue them freely.

// STARTUP and every frame, whatever the screen

/** Loads the art and sounds, shares the [Game] and builds the level's scenery, which every screen shows. */
fun startGame(app: App, isAutopilot: Boolean) {
    val game = Game(Art.load(app.assets), Sounds.load(app.assets), isAutopilot)
    app.resources.insert(game)
    app.commands.spawnScenery(app.assets, game.art)
    app.camera.bounds = Rect(0f, 0f, RooftopRun.WIDTH, Tuning.WORLD_HEIGHT)
    fitCamera(app)
}

/**
 * Zooms the camera so the level's height fills the window, whatever its size; a wider window
 * shows more of the level to the sides. The viewport is in points, so the zoom is points per unit.
 */
fun fitCamera(app: App) {
    app.camera.zoom = app.camera.viewportHeight / Tuning.WORLD_HEIGHT
}

/** Title scene LATE_UPDATE system: drifts the camera along the level and back to the start at its end. */
fun panTitleCamera(app: App) {
    val game = app.game
    game.titleCameraX += Tuning.TITLE_PAN_SPEED * app.time.deltaSeconds
    val half = app.camera.visibleWidth / 2
    if (game.titleCameraX > RooftopRun.WIDTH - half) game.titleCameraX = half
    app.camera.lookAt(game.titleCameraX.coerceAtLeast(half), Tuning.WORLD_HEIGHT / 2)
}

// Play scene: entering

/** Starts a run: spawns the hero, the pickups and the enemies, which belong to the play scene, and shares a new [Run]. */
fun startRun(app: App) {
    val game = app.game
    val player = app.commands.spawnPlayer(game.art)
    app.commands.spawnPickupsAndEnemies(game.art, game.random)
    app.resources.insert(Run(game, player, startStep = app.time.fixedStepCount))
    game.startMusic(app)
    app.audio.music.volume = Tuning.MUSIC_VOLUME
    app.camera.lookAt(RooftopRun.start.x, Tuning.WORLD_HEIGHT / 2)
}

// FIXED_UPDATE

/** Runs the run's clock while the hero is under control. */
fun Run.tickClock(app: App) {
    if (state == RunState.PLAYING) elapsed += app.time.deltaSeconds
}

/**
 * Steers the hero for this fixed step: under the player's control while playing, still while he
 * is out of sight after a fall, tumbling away after the last heart, and gliding to a stop at the
 * sign. It sets his `KinematicBody`'s speed, which the engine then moves him by, stopping him at
 * the roofs; the contacts it reads are those the previous step's move left.
 */
fun Run.movePlayer(app: App) {
    val dt = app.time.deltaSeconds
    val world = app.world
    val hero = world.getOrNull(player, Player) ?: return
    val body = world.get(player, KinematicBody)
    val transform = world.get(player, Transform)
    val controls = game.controls
    val isGrounded = body.isGrounded
    hero.isHiddenForStep = false
    hero.previousX = transform.x
    hero.previousFeet = transform.feet
    when (state) {
        RunState.PLAYING -> {
            hero.hurtLock -= dt
            hero.invulnerable -= dt
            if (isGrounded && !hero.wasGrounded && hero.fallSpeed > Tuning.DUST_LANDING_SPEED) {
                app.commands.spawnDust(game.art, transform.x, transform.feet)
            }
            val roof = body.floorOrNull?.let { world.getOrNull(it, Block) }
            if (isGrounded && roof != null && !roof.isLedge) {
                hero.safeRoof = roof
                hero.safeX = transform.x
            }
            val hasControl = hero.hurtLock <= 0f
            val move = if (hasControl) controls.move else 0f
            val isPressed = if (game.isAutopilot) controls.isJumpPressed else isJumpJustPressed(app.input)
            // The run's first step can still see the press that started it on the title screen.
            val jump = isPressed && hasControl && app.time.fixedStepCount > startStep
            body.dx = rollSpeed(body.dx, move, isGrounded, dt)
            // Jumping, with a little grace either side of being on a roof.
            hero.jumpBuffer = if (jump) Tuning.JUMP_BUFFER_SECONDS else hero.jumpBuffer - dt
            hero.coyote = if (isGrounded) Tuning.COYOTE_SECONDS else hero.coyote - dt
            if (hero.jumpBuffer > 0f && hero.coyote > 0f) {
                body.dy = -Tuning.JUMP_SPEED
                hero.coyote = 0f
                hero.jumpBuffer = 0f
                app.audio.play(game.sounds.jump, volume = 0.6f)
            }
            body.dy = fallSpeed(body.dy, controls.isJumpHeld, dt)
            if (move != 0f) hero.facing = sign(move)
            if (transform.feet > Tuning.WORLD_HEIGHT + Tuning.PIT_MARGIN) fall(app)
        }
        RunState.FALLING -> {
            body.dx = 0f
            body.dy = 0f
            stateTimer -= dt
            if (stateTimer <= 0f) {
                if (hearts == 0) outcomeOrNull = RunOutcome.GAME_OVER else respawn(hero, body, transform)
            }
        }
        RunState.DYING -> {
            // His collider no longer looks for the level, so the body falls through the roofs.
            body.dy += Tuning.GRAVITY * dt
            stateTimer -= dt
            if (stateTimer <= 0f) outcomeOrNull = RunOutcome.GAME_OVER
        }
        RunState.FINISHING -> {
            // Brakes against the roll until nearly still, so he stops by the sign, not past it.
            val brake = if (abs(body.dx) > 20f) -sign(body.dx) else 0f
            body.dx = rollSpeed(body.dx, brake, isGrounded, dt)
            body.dy = fallSpeed(body.dy, isHeld = false, dt = dt)
            stateTimer -= dt
            if (stateTimer <= 0f) outcomeOrNull = RunOutcome.COMPLETE
        }
    }
    hero.wasGrounded = isGrounded
    hero.fallSpeed = body.dy
    transform.rotation = when {
        state == RunState.DYING -> transform.rotation + 8f * dt * -hero.facing
        isGrounded -> hero.facing * Tuning.LEAN * min(1f, abs(body.dx) / Tuning.MAX_SPEED)
        else -> 0f
    }
}

/**
 * Whether the body stands on a roof: the last move ended on one and nothing has sent it up since.
 * A stomp or a hit sets [KinematicBody.dy] rising while [KinematicBody.isOnFloor] still says
 * where the last move ended, and on a roof the move zeroes `dy`, so a rising `dy` means he has
 * left it.
 */
val KinematicBody.isGrounded: Boolean get() = isOnFloor && dy >= 0f

/** The y of the hero's feet, the bottom of his box, whose Transform is the middle of his sprite. */
val Transform.feet: Float get() = y + Tuning.PLAYER_SPRITE_HEIGHT / 2

/**
 * Returns the hero's horizontal speed after [dt] seconds of rolling from [dx] with [move] from -1
 * to 1 pressed: pushing speeds him up, pushing back brakes hard, and letting go glides.
 */
fun rollSpeed(dx: Float, move: Float, isGrounded: Boolean, dt: Float): Float {
    if (move == 0f) return approach(dx, 0f, (if (isGrounded) Tuning.GLIDE_FRICTION else Tuning.AIR_DRAG) * dt)
    val isBraking = dx != 0f && sign(move) != sign(dx)
    val acceleration = when {
        isBraking -> Tuning.TURN_DECELERATION
        isGrounded -> Tuning.GROUND_ACCELERATION
        else -> Tuning.AIR_ACCELERATION
    }
    if (isBraking || abs(dx) < Tuning.MAX_SPEED) return (dx + move * acceleration * dt).coerceIn(-Tuning.MAX_SPEED, Tuning.MAX_SPEED)
    // Faster than the top speed after a knockback: ease back down to it.
    return approach(dx, sign(dx) * Tuning.MAX_SPEED, Tuning.GLIDE_FRICTION * dt)
}

/**
 * Returns the hero's vertical speed after [dt] seconds of gravity from [dy]: stronger while he
 * rises with the jump button up, which cuts a jump short, and never faster than the fall limit.
 */
fun fallSpeed(dy: Float, isHeld: Boolean, dt: Float): Float {
    val gravity = if (dy < 0f && !isHeld) Tuning.GRAVITY * Tuning.SHORT_HOP_GRAVITY else Tuning.GRAVITY
    return min(dy + gravity * dt, Tuning.MAX_FALL_SPEED)
}

/** Returns [value] moved toward [target] by at most [amount]. */
private fun approach(value: Float, target: Float, amount: Float): Float =
    if (value < target) min(value + amount, target) else max(value - amount, target)

/** The hero fell into a gap: a heart is gone, and he waits out of sight before coming back. */
private fun Run.fall(app: App) {
    hearts--
    state = RunState.FALLING
    stateTimer = FALL_SECONDS
    shake = Tuning.CAMERA_SHAKE_SECONDS
    app.audio.play(game.sounds.hurt)
    println("Fell into a gap at ${app.world.get(player, Transform).x.toInt()}: $hearts hearts left.")
}

private const val FALL_SECONDS = 0.8f

/**
 * Puts the hero back on the last roof he stood on, a little behind where he left it, blinking.
 * He jumps there in one step, which the engine would draw as a dash across the screen between
 * two steps, so he stays hidden until the next step has recorded the new place.
 */
private fun Run.respawn(hero: Player, body: KinematicBody, transform: Transform) {
    val roof = hero.safeRoof
    val x = (hero.safeX - Tuning.RESPAWN_BACKSET).coerceIn(roof.left + Tuning.RESPAWN_EDGE_MARGIN, roof.right - Tuning.RESPAWN_EDGE_MARGIN)
    transform.x = x
    transform.y = roof.top - Collisions.CONTACT_GAP - Tuning.PLAYER_SPRITE_HEIGHT / 2
    body.dx = 0f
    body.dy = 0f
    hero.previousX = x
    hero.previousFeet = transform.feet
    hero.facing = 1f
    hero.coyote = 0f
    hero.jumpBuffer = 0f
    hero.invulnerable = Tuning.INVULNERABLE_SECONDS * 1.5f
    hero.hurtLock = 0f
    hero.isHiddenForStep = true
    state = RunState.PLAYING
}

/**
 * Walks every blob along its roof, turning at the ends of its patrol and wobbling as it goes,
 * and flies every helicopter back and forth on a wave.
 */
fun moveEnemies(app: App) {
    val dt = app.time.deltaSeconds
    app.world.forEach(Enemy, Transform) { _, enemy, transform ->
        enemy.phase += dt
        val speed = if (enemy.kind == EnemyKind.BLOB) Tuning.BLOB_SPEED else Tuning.HELI_SPEED
        val patrol = enemy.patrol
        transform.x += enemy.direction * speed * dt
        if (transform.x >= patrol.maxX) {
            transform.x = patrol.maxX
            enemy.direction = -1f
        } else if (transform.x <= patrol.minX) {
            transform.x = patrol.minX
            enemy.direction = 1f
        }
        if (enemy.kind == EnemyKind.BLOB) {
            val wobble = Tuning.BLOB_WOBBLE * sin(enemy.phase * Tuning.BLOB_WOBBLE_RATE)
            transform.scaleY = 1f + wobble
            transform.scaleX = 1f - wobble
            // Squashed about its bottom, so it stays on the roof.
            transform.y = patrol.y - Tuning.BLOB_HEIGHT * transform.scaleY / 2
        } else {
            transform.y = patrol.y + Tuning.HELI_BOB * sin(enemy.phase * Tuning.HELI_BOB_RATE)
        }
    }
}

/** Spins the stomped helicopters and pulls them down; the engine moves them by their [Velocity]. */
fun tumble(app: App) {
    val dt = app.time.deltaSeconds
    app.world.forEach(Tumble, Velocity, Transform) { _, tumble, velocity, transform ->
        velocity.dy += Tuning.GRAVITY * dt
        transform.rotation += tumble.spin * dt
    }
}

// UPDATE

/** Spins the rings, bobs the hearts, and turns the enemies the way they go. */
fun animatePickups(app: App) {
    val seconds = app.time.elapsed.inWholeMilliseconds / 1000f
    val world = app.world
    world.forEach(Ring, Transform) { _, ring, transform ->
        transform.scaleX = max(0.15f, abs(cos(seconds * Tuning.RING_SPIN_RATE + ring.phase)))
    }
    world.forEach(HeartPickup, Transform) { _, heart, transform ->
        transform.y = heart.baseY + Tuning.HEART_BOB * sin(seconds * 3f)
    }
    world.forEach(Enemy, Sprite) { _, enemy, sprite ->
        // The helicopter art faces left, the blob looks either way.
        sprite.isFlippedX = if (enemy.kind == EnemyKind.HELI) enemy.direction > 0f else enemy.direction < 0f
    }
}

/**
 * Handles everything the hero touched this frame: rings and hearts are taken, an enemy he comes
 * down on is stomped, and one he runs into hurts him.
 */
fun Run.resolveOverlaps(app: App) {
    val world = app.world
    val hero = world.getOrNull(player, Player) ?: return
    if (state != RunState.PLAYING) return
    app.events.forEach<Overlap> { overlap ->
        if (overlap.a != player && overlap.b != player) return@forEach
        val other = overlap.otherThan(player)
        val ring = world.getOrNull(other, Ring)
        if (ring != null) {
            if (!ring.isTaken) takeRing(app, other, ring)
            return@forEach
        }
        val heart = world.getOrNull(other, HeartPickup)
        if (heart != null) {
            if (!heart.isTaken) takeHeart(app, other, heart)
            return@forEach
        }
        val enemy = world.getOrNull(other, Enemy) ?: return@forEach
        if (enemy.isStomped) return@forEach
        val transform = world.get(other, Transform)
        val height = if (enemy.kind == EnemyKind.BLOB) Tuning.BLOB_HEIGHT else Tuning.HELI_HEIGHT
        val top = transform.y - height / 2
        val body = world.get(player, KinematicBody)
        // Coming down onto it, with the feet above its top before the last step: a stomp.
        if (body.dy > 0f && hero.previousFeet <= top + Tuning.STOMP_TOLERANCE) {
            stomp(app, hero, body, other, enemy, transform, top)
        } else {
            hurt(app, hero, body, transform.x)
        }
    }
}

private fun Run.takeRing(app: App, entity: Entity, ring: Ring) {
    val transform = app.world.get(entity, Transform)
    ring.isTaken = true
    app.commands.destroy(entity)
    rings++
    score += Tuning.RING_POINTS
    game.totalRings++
    app.commands.spawnSparkle(game.art, transform.x, transform.y, RING_SPARKLE)
    // Every other ring a little higher, so a line of them runs up a scale.
    app.audio.play(game.sounds.ring, volume = 0.5f, pitch = if (rings % 2 == 0) 1.06f else 1f)
}

private val RING_SPARKLE = Color(0xFFE45CFFu)
private val HEART_SPARKLE = Color(0xFF5A6EFFu)

private fun Run.takeHeart(app: App, entity: Entity, heart: HeartPickup) {
    val transform = app.world.get(entity, Transform)
    heart.isTaken = true
    app.commands.destroy(entity)
    if (hearts < Tuning.MAX_HEARTS) {
        hearts++
    } else {
        score += Tuning.HEART_POINTS
        app.commands.spawnPopup(app.assets.defaultFont, "100", transform.x, transform.y - 20f)
    }
    app.commands.spawnSparkle(game.art, transform.x, transform.y, HEART_SPARKLE)
    app.audio.play(game.sounds.heart, volume = 0.7f)
}

private fun Run.stomp(app: App, hero: Player, body: KinematicBody, entity: Entity, enemy: Enemy, transform: Transform, top: Float) {
    enemy.isStomped = true
    app.commands.destroy(entity)
    body.dy = -(if (game.controls.isJumpHeld) Tuning.STOMP_BOUNCE_HELD else Tuning.STOMP_BOUNCE)
    hero.coyote = 0f
    stomps++
    game.totalStomps++
    score += Tuning.STOMP_POINTS
    val commands = app.commands
    commands.spawnImpact(game.art, game.random, (app.world.get(player, Transform).x + transform.x) / 2, top)
    // Behind the hero, who bounces up and on, so the points stay in sight.
    commands.spawnPopup(app.assets.defaultFont, "150", transform.x - hero.facing * 44f, top - 18f)
    if (enemy.kind == EnemyKind.BLOB) {
        commands.spawnSquashedBlob(game.art, transform.x, enemy.patrol.y, isFlipped = enemy.direction < 0f)
    } else {
        commands.spawnFallingHeli(game.art, transform.x, transform.y, enemy.direction)
    }
    app.audio.play(game.sounds.stomp)
}

/** The hero ran into an enemy at [enemyX]: a heart is gone and he is knocked away from it, blinking, unless he is still blinking. */
private fun Run.hurt(app: App, hero: Player, body: KinematicBody, enemyX: Float) {
    if (hero.invulnerable > 0f) return
    val x = app.world.get(player, Transform).x
    hearts--
    body.dx = if (x < enemyX) -Tuning.KNOCKBACK_X else Tuning.KNOCKBACK_X
    body.dy = -Tuning.KNOCKBACK_Y
    hero.hurtLock = Tuning.HURT_LOCK_SECONDS
    hero.invulnerable = Tuning.INVULNERABLE_SECONDS
    shake = Tuning.CAMERA_SHAKE_SECONDS
    app.audio.play(game.sounds.hurt)
    println("Hit by an enemy at ${x.toInt()}: $hearts hearts left.")
    if (hearts == 0) {
        state = RunState.DYING
        stateTimer = DYING_SECONDS
        body.dy = -Tuning.JUMP_SPEED * 0.8f
        app.world.get(player, Collider).mask = Layers.DYING_PLAYER_MASK
    }
}

private const val DYING_SECONDS = 1.3f

/** Ends the run well once the hero reaches the sign: works out the bonuses and lets him glide to a stop. */
fun Run.checkGoal(app: App) {
    if (state != RunState.PLAYING) return
    val transform = app.world.getOrNull(player, Transform) ?: return
    if (transform.x < RooftopRun.SIGN_X - 20f) return
    state = RunState.FINISHING
    stateTimer = FINISH_SECONDS
    heartBonus = hearts * Tuning.HEART_BONUS
    timeBonus = max(0, ((Tuning.PAR_SECONDS - elapsed) * Tuning.TIME_BONUS_PER_SECOND).toInt())
    game.levelsCompleted++
    game.bestScore = max(game.bestScore, finalScore)
    app.audio.music.volume = Tuning.END_MUSIC_VOLUME
    app.audio.play(game.sounds.complete)
    println(
        "Level complete in ${(elapsed * 10).toInt() / 10f} s: score $score, $rings rings, $stomps stomps, " +
            "$hearts hearts, final $finalScore.",
    )
}

private const val FINISH_SECONDS = 1.2f

/**
 * Shows the hero: the skating frames in turn as he rolls, faster the faster he goes, the jump
 * frame in the air, mirrored when he faces left, blinking while he cannot be hurt.
 */
fun Run.animatePlayer(app: App) {
    val hero = app.world.getOrNull(player, Player) ?: return
    val sprite = app.world.get(player, Sprite)
    val body = app.world.get(player, KinematicBody)
    val art = game.art
    val region = when {
        !body.isGrounded -> art.jump
        abs(body.dx) > 15f -> {
            hero.stride += abs(body.dx) * app.time.deltaSeconds / Tuning.STRIDE_LENGTH
            art.skate[hero.stride.toInt() % art.skate.size]
        }
        else -> art.skate[0]
    }
    sprite.region = region
    sprite.width = sprite.height * region.width / region.height
    sprite.isFlippedX = hero.facing < 0f
    val isBlinkedOut = hero.invulnerable > 0f && (hero.invulnerable / Tuning.BLINK_SECONDS).toInt() % 2 == 1
    sprite.isVisible = state != RunState.FALLING && !hero.isHiddenForStep && !isBlinkedOut
}

// LATE_UPDATE

/**
 * Eases the camera after the hero, looking ahead the way he rolls, and shakes it after a hit. The
 * hero is drawn between his last two fixed steps, so the camera follows that drawn place, not his
 * Transform, or he would judder against the scenery on a fast display.
 */
fun Run.followCamera(app: App) {
    val hero = app.world.getOrNull(player, Player) ?: return
    val x = app.world.get(player, Transform).x
    val speed = app.world.get(player, KinematicBody).dx
    val camera = app.camera
    val dt = app.time.deltaSeconds
    val alpha = app.time.fixedStepAlpha
    val drawnX = hero.previousX + (x - hero.previousX) * alpha
    val ahead = hero.facing * Tuning.CAMERA_LOOK_AHEAD * min(1f, abs(speed) / Tuning.MAX_SPEED)
    lookAhead += (ahead - lookAhead) * (1f - exp(-Tuning.CAMERA_LOOK_AHEAD_SMOOTHING * dt))
    // Take last frame's shake out before easing, so it does not build up in the camera's position.
    camera.x -= shakeOffset
    camera.follow(drawnX + lookAhead, Tuning.WORLD_HEIGHT / 2, smoothing = Tuning.CAMERA_SMOOTHING, delta = app.time.delta)
    shakeOffset = 0f
    if (shake > 0f) {
        shake -= dt
        shakeOffset = (game.random.nextFloat() * 2f - 1f) * Tuning.CAMERA_SHAKE * (shake / Tuning.CAMERA_SHAKE_SECONDS)
        camera.x += shakeOffset
        camera.clampToBounds()
    }
}
