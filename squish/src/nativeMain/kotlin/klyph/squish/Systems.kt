package klyph.squish

import klyph.app.App
import klyph.app.deltaSeconds
import klyph.collision.Collider
import klyph.collision.Overlap
import klyph.collision.Shape
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.render.Sprite
import klyph.render.lookAt
import klyph.render.visibleWidth
import klyph.text.Text
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The game's systems. GameScenes adds them to its scenes in the order they appear here. Spawns
// and destroys go through app.commands, which the engine applies after each system, so queries
// can queue them freely.

// STARTUP and every frame, whatever the screen

/** Loads the art and sounds and shares the [Game]. */
fun startGame(app: App, isAutopilot: Boolean) {
    app.resources.insert(Game(Art.load(app.assets), Sounds.load(app.assets), isAutopilot))
    fitCamera(app)
}

/**
 * Fits the whole world into the window, centred, so the blanket fills the same share of the window
 * at any size. The camera's viewport is in pixels, so the zoom is pixels per world unit.
 */
fun fitCamera(app: App) {
    val camera = app.camera
    camera.zoom = min(camera.viewportWidth / Tuning.WORLD_WIDTH, camera.viewportHeight / Tuning.WORLD_HEIGHT)
    camera.lookAt(Tuning.WORLD_WIDTH / 2, Tuning.WORLD_HEIGHT / 2)
}

/** Counts down the swatter's slap. */
fun settleSwatter(app: App) {
    val game = app.game
    game.slapTimer = (game.slapTimer - app.time.deltaSeconds).coerceAtLeast(0f)
}

// Play scene: entering

/** Starts a round: lays out the picnic, which belongs to the play scene, shares a new [Round] and rings the start chime. */
fun startRound(app: App) {
    val game = app.game
    val food = app.commands.spawnPicnic(game.art)
    app.resources.insert(Round(game, food))
    game.startMusic(app)
    app.audio.music.volume = 1f
    app.audio.play(game.sounds.start)
}

// FIXED_UPDATE

/** Runs the round's clock and ends the round when the time is up. */
fun Round.tickClock(app: App) {
    elapsed += app.time.deltaSeconds
    if (elapsed >= Tuning.ROUND_SECONDS && endOrNull == null) endOrNull = RoundEnd.TIME_UP
}

/**
 * Sends in a bug whenever the spawn timer runs out, more often as the round goes on, while fewer
 * than [Tuning.MAX_BUGS] are about and some food is left.
 */
fun Round.spawnBugs(app: App) {
    spawnTimer -= app.time.deltaSeconds
    while (spawnTimer <= 0f) {
        spawnTimer += lerp(Tuning.SPAWN_INTERVAL_START, Tuning.SPAWN_INTERVAL_END, progress)
        if (app.world.count(Bug) >= Tuning.MAX_BUGS) continue
        val random = game.random
        // A point just outside the world, anywhere along its edge.
        val width = Tuning.WORLD_WIDTH
        val height = Tuning.WORLD_HEIGHT
        val margin = Tuning.SPAWN_MARGIN
        val along = random.nextFloat() * 2f * (width + height)
        val x: Float
        val y: Float
        when {
            along < width -> { x = along; y = -margin }
            along < width + height -> { x = width + margin; y = along - width }
            along < 2 * width + height -> { x = along - width - height; y = height + margin }
            else -> { x = -margin; y = along - 2 * width - height }
        }
        val food = nearestFoodOrNull(app.world, x, y) ?: return
        val target = app.world.get(food, Transform)
        app.commands.spawnBug(game.art, random, food, target.x, target.y, x, y, lerp(Tuning.SPEED_FACTOR_START, Tuning.SPEED_FACTOR_END, progress))
    }
}

/**
 * Walks every bug: a hungry one toward its food, picking the nearest food left when its own is
 * gone, and a full one off the blanket, away from where it ate; it is removed once out of sight.
 * Every bug wiggles from side to side as it goes and waddles a little.
 */
fun Round.walkBugs(app: App) {
    val dt = app.time.deltaSeconds
    val world = app.world
    val commands = app.commands
    world.forEach(Bug, Transform) { entity, bug, transform ->
        bug.wigglePhase += Tuning.WIGGLE_RATE * dt
        if (!bug.isFull && !isEdible(world, bug.target)) {
            val food = nearestFoodOrNull(world, transform.x, transform.y)
            if (food != null) {
                bug.target = food
            } else {
                // Nothing left to eat: leave the way it came.
                bug.isFull = true
                bug.fleeX = Tuning.CAKE_X
                bug.fleeY = Tuning.CAKE_Y
            }
        }
        val heading = if (bug.isFull) {
            headingOf(transform.x - bug.fleeX, transform.y - bug.fleeY)
        } else {
            val food = world.get(bug.target, Transform)
            headingOf(food.x - transform.x, food.y - transform.y)
        }
        val direction = heading + Tuning.WIGGLE_ANGLE * sin(bug.wigglePhase)
        val speed = if (bug.isFull) bug.speed * Tuning.FULL_SPEED_FACTOR else bug.speed
        transform.x += stepX(direction, speed * dt)
        transform.y += stepY(direction, speed * dt)
        transform.rotation = direction
        transform.scaleX = 1f + Tuning.WADDLE_SCALE * sin(2f * bug.wigglePhase)
        if (bug.isFull && isOutside(transform.x, transform.y, Tuning.EXIT_MARGIN)) commands.destroy(entity)
    }
}

/** Returns the food with bites left nearest to ([x], [y]), or `null` when the bugs have eaten it all. */
fun Round.nearestFoodOrNull(world: World, x: Float, y: Float): Entity? {
    var nearest: Entity? = null
    var nearestDistance = Float.POSITIVE_INFINITY
    for (index in food.indices) {
        val entity = food[index]
        if (!isEdible(world, entity)) continue
        val transform = world.get(entity, Transform)
        val dx = transform.x - x
        val dy = transform.y - y
        val distance = dx * dx + dy * dy
        if (distance < nearestDistance) {
            nearest = entity
            nearestDistance = distance
        }
    }
    return nearest
}

/** Returns `true` if [entity] is food with a bite left. */
private fun isEdible(world: World, entity: Entity): Boolean = (world.getOrNull(entity, Food)?.bites ?: 0) > 0

private fun isOutside(x: Float, y: Float, margin: Float): Boolean =
    x < -margin || x > Tuning.WORLD_WIDTH + margin || y < -margin || y > Tuning.WORLD_HEIGHT + margin

/** Walks the title screen's bugs around their ellipses, each facing the way it goes. */
fun orbitBugs(app: App) {
    val dt = app.time.deltaSeconds
    app.world.forEach(Orbit, Transform) { _, orbit, transform ->
        orbit.angle += orbit.rate * dt
        val c = cos(orbit.angle)
        val s = sin(orbit.angle)
        transform.x = orbit.centerX + orbit.radiusX * c
        transform.y = orbit.centerY + orbit.radiusY * s
        // Moving along the ellipse is a step of (-radiusX sin, radiusY cos) times the rate.
        transform.rotation = headingOf(-orbit.radiusX * s * orbit.rate, orbit.radiusY * c * orbit.rate)
    }
}

// UPDATE

/**
 * Brings the swatter down if the player swatted: every bug under its head is squished, scores and
 * leaves a splat. The engine's collision query finds them, from this frame's positions.
 */
fun Round.swat(app: App) {
    val controls = game.controls
    if (!controls.isSwatPressed) return
    val hits = game.hits
    hits.clear()
    app.collisions.overlapsTo(hits, SWATTER, x = controls.cursorX, y = controls.cursorY, mask = Layers.BUG)
    for (index in hits.indices) {
        val entity = hits[index]
        // The query reads the last detection, so a bug destroyed since then can still turn up.
        val bug = app.world.getOrNull(entity, Bug) ?: continue
        if (bug.isSquished) continue
        squish(app, entity, bug)
    }
}

/** The swatter's head, for the collision query. */
private val SWATTER = Shape.Circle(Tuning.SWAT_RADIUS)

private fun Round.squish(app: App, entity: Entity, bug: Bug) {
    val transform = app.world.get(entity, Transform)
    bug.isSquished = true
    app.commands.spawnSquish(game.art, app.assets.defaultFont, bug.kind, transform.x, transform.y, transform.rotation, game.random)
    app.commands.destroy(entity)
    score++
    game.totalSquished++
    val pitch = Tuning.SQUISH_MIN_PITCH + game.soundRandom.nextFloat() * (Tuning.SQUISH_MAX_PITCH - Tuning.SQUISH_MIN_PITCH)
    app.audio.play(game.sounds.squish, pitch = pitch, pan = panOf(app, transform.x))
}

/**
 * Lets every hungry bug that reached food take a bite: the food shrinks and drops crumbs, the bug
 * turns for home, and food with no bites left disappears. Ends the round when the picnic is eaten.
 */
fun Round.feedBugs(app: App) {
    val world = app.world
    app.events.forEach<Overlap> { overlap ->
        // The pair comes ordered by entity index, so the bug may be either one.
        val bugEntity = if (world.has(overlap.a, Bug)) overlap.a else overlap.b
        val foodEntity = if (bugEntity == overlap.a) overlap.b else overlap.a
        val bug = world.getOrNull(bugEntity, Bug) ?: return@forEach
        val food = world.getOrNull(foodEntity, Food) ?: return@forEach
        if (bug.isFull || bug.isSquished || food.bites == 0) return@forEach
        bite(app, bug, world.get(bugEntity, Transform), foodEntity, food)
    }
    if (endOrNull == null && nearestFoodOrNull(world, 0f, 0f) == null) endOrNull = RoundEnd.FOOD_EATEN
}

private fun Round.bite(app: App, bug: Bug, transform: Transform, foodEntity: Entity, food: Food) {
    val world = app.world
    val foodTransform = world.get(foodEntity, Transform)
    food.bites--
    bug.isFull = true
    bug.fleeX = foodTransform.x
    bug.fleeY = foodTransform.y
    app.commands.spawnCrumbs(game.art, food, transform.x, transform.y)
    app.audio.play(game.sounds.munch, volume = Tuning.MUNCH_VOLUME, pan = panOf(app, transform.x))
    val sprite = world.get(foodEntity, Sprite)
    if (food.bites == 0) {
        // Kept, hidden and out of reach, so the HUD still knows what was there.
        sprite.isVisible = false
        app.commands.remove(foodEntity, Collider)
        return
    }
    val scale = lerp(Tuning.FOOD_MIN_SCALE, 1f, food.bites.toFloat() / food.maxBites)
    sprite.width = food.baseSize * scale
    sprite.height = food.baseSize * scale
    world.get(foodEntity, Collider).shape = Shape.Circle(food.baseRadius * scale)
}

/** Moves the entities that drift, the rising "+1"s. */
fun drift(app: App) {
    val dt = app.time.deltaSeconds
    app.world.forEach(Velocity, Transform) { _, velocity, transform ->
        transform.x += velocity.x * dt
        transform.y += velocity.y * dt
    }
}

/** Fades splats, squashed bugs and "+1"s, and removes them once they are clear. */
fun fade(app: App) {
    val dt = app.time.deltaSeconds
    val commands = app.commands
    app.world.forEach(Fade, Sprite) { entity, fade, sprite ->
        val alpha = fade.advance(dt)
        if (alpha <= 0f) commands.destroy(entity) else sprite.color = fade.color.withAlpha(fade.color.alpha * alpha)
    }
    app.world.forEach(Fade, Text) { entity, fade, text ->
        val alpha = fade.advance(dt)
        if (alpha <= 0f) commands.destroy(entity) else text.color = fade.color.withAlpha(fade.color.alpha * alpha)
    }
}

/** Ages the fade by [dt] and returns how much of its color shows, from 1 down to 0 once it is over. */
private fun Fade.advance(dt: Float): Float {
    age += dt
    return (1f - (age - hold) / duration).coerceIn(0f, 1f)
}

/** Destroys the carriers of particle bursts whose time is up. */
fun expire(app: App) {
    val dt = app.time.deltaSeconds
    val commands = app.commands
    app.world.forEach(Lifetime) { entity, lifetime ->
        lifetime.remaining -= dt
        if (lifetime.remaining <= 0f) commands.destroy(entity)
    }
}

/**
 * Records the score once the round is over and returns `true`, so the play scene shows the end
 * screen; returns `false` while it goes on.
 */
fun Round.finishIfOver(): Boolean {
    val end = endOrNull ?: return false
    game.lastScore = score
    game.bestScore = maxOf(game.bestScore, score)
    game.roundsPlayed++
    println("Round over (${if (end == RoundEnd.TIME_UP) "time up" else "food eaten"}): $score bugs squished.")
    return true
}

/** Returns the pan, from -1 to 1, of a sound at world [x], by where it is across the view. */
private fun panOf(app: App, x: Float): Float {
    val camera = app.camera
    return ((x - camera.x) / (camera.visibleWidth / 2)).coerceIn(-1f, 1f)
}

/** Returns the value [t] of the way from [from] to [to]. */
fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
