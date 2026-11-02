package klyph.shooter

import klyph.platform.Color
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Every tuning value of the game in one place. Distances are world units, times are seconds. */
object Tuning {
    const val WINDOW_WIDTH = 1280
    const val WINDOW_HEIGHT = 720
    val SPACE_COLOR = Color(red = 0.015f, green = 0.015f, blue = 0.05f)

    /** Rate at which the camera closes the gap to the player, per second. */
    const val CAMERA_FOLLOW_RATE = 5f

    const val ARENA_WIDTH = 2560f
    const val ARENA_HEIGHT = 1440f

    const val LAYER_STARS = 0
    const val LAYER_BULLETS = 1
    const val LAYER_SHIPS = 2
    const val LAYER_EFFECTS = 3

    const val STAR_COUNT = 600
    const val STAR_MIN_SIZE = 1f
    const val STAR_MAX_SIZE = 3f
    const val STAR_MIN_ALPHA = 0.15f
    const val STAR_MAX_ALPHA = 0.6f

    const val SHIP_SIZE = 48f
    /** Share of a ship's half size that counts for collisions; the art does not fill its square. */
    const val SHIP_HITBOX_SCALE = 0.7f
    /** Collider radius of the player's ship and of an enemy. */
    const val SHIP_RADIUS = SHIP_SIZE / 2 * SHIP_HITBOX_SCALE

    const val PLAYER_SPEED = 400f
    const val PLAYER_LIVES = 3
    const val RESPAWN_DELAY = 1f
    /** Time after a respawn during which enemies pass through the player, which blinks. */
    const val INVULNERABLE_TIME = 2f
    const val BLINK_RATE = 8f

    const val FIRE_INTERVAL = 0.12f
    /** How far a gamepad trigger, from 0 to 1, must be pressed to fire. */
    const val TRIGGER_THRESHOLD = 0.5f
    /** A cursor closer to the ship than this gives no direction to face. */
    const val MIN_AIM_DISTANCE = 4f
    const val BULLET_WIDTH = 14f
    const val BULLET_HEIGHT = 26f
    const val BULLET_SPEED = 700f
    const val BULLET_LIFETIME = 1.5f
    /** Collider radius of a bullet: half its narrow side. */
    const val BULLET_RADIUS = BULLET_WIDTH / 2

    const val ENEMY_SPAWN_INTERVAL = 0.8f
    const val ENEMY_MAX_ALIVE = 40
    const val ENEMY_MIN_SPEED = 120f
    const val ENEMY_MAX_SPEED = 200f
    const val ENEMY_SWAY_AMPLITUDE = 70f
    /** Angular speed of the sway in radians per second. */
    const val ENEMY_SWAY_MIN_RATE = 0.8f
    const val ENEMY_SWAY_MAX_RATE = 2f
    /** Bank angle in radians at the fastest point of the sway. */
    const val ENEMY_BANK = 0.25f
    const val ENEMY_SCORE = 10

    const val EXPLOSION_SIZE = 96f
    val EXPLOSION_FRAME: Duration = 60.milliseconds

    /** Side of the white square every particle is drawn from, in texels. */
    const val PARTICLE_SIZE = 4
    const val BURST_PARTICLES = 24
    const val BURST_MIN_SPEED = 80f
    const val BURST_MAX_SPEED = 220f
    const val BURST_MIN_LIFETIME = 0.3f
    const val BURST_MAX_LIFETIME = 0.7f
    /** Seconds the entity carrying a burst lives; longer than any of its particles. */
    const val BURST_ENTITY_LIFETIME = 1f
    val BURST_START_COLOR = Color(red = 1f, green = 0.6f, blue = 0.15f)
    val BURST_END_COLOR = Color(red = 1f, green = 0.1f, blue = 0.05f, alpha = 0f)
    const val BURST_START_SCALE = 1.5f
    const val BURST_END_SCALE = 0.3f

    /** Particles per second of the ship's exhaust while it flies. */
    const val THRUSTER_RATE = 60f
    const val THRUSTER_CAPACITY = 32
    /** Distance from the ship's center to the exhaust, toward its tail. */
    const val THRUSTER_OFFSET = SHIP_SIZE * 0.4f
    /** Width of the exhaust's fan of directions, in degrees. */
    const val THRUSTER_SPREAD = 30f
    const val THRUSTER_MIN_SPEED = 40f
    const val THRUSTER_MAX_SPEED = 90f
    const val THRUSTER_MIN_LIFETIME = 0.2f
    const val THRUSTER_MAX_LIFETIME = 0.4f
    val THRUSTER_START_COLOR = Color(red = 0.45f, green = 0.75f, blue = 1f)
    val THRUSTER_END_COLOR = Color(red = 0.2f, green = 0.4f, blue = 1f, alpha = 0f)
    const val THRUSTER_START_SCALE = 1f
    const val THRUSTER_END_SCALE = 0.3f

    /** Scale of the built-in 8x8 font for titles and for the rest of the screen text. */
    const val TITLE_TEXT_SCALE = 4f
    const val TEXT_SCALE = 2f
    /** Color of hints and small print, dimmer than the rest of the text. */
    val HINT_COLOR = Color(red = 0.6f, green = 0.65f, blue = 0.75f)
    /** Vertical distance between lines of screen text at [TEXT_SCALE], in points. */
    const val LINE_SPACING = 28f
    /** Distance of the score and lives from the window's top-left corner, in points. */
    const val HUD_MARGIN = 16f
    /** Darkening laid over the frozen game while it is paused. */
    val PAUSE_SHADE = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.5f)
    /** Seconds the game over screen waits before the autopilot starts another game. */
    val AUTOPILOT_RETRY_DELAY: Duration = 1.seconds

    const val SHOT_VOLUME = 0.6f
    /** Each shot plays at a random pitch in this range, so rapid fire does not drone. */
    const val SHOT_MIN_PITCH = 0.95f
    const val SHOT_MAX_PITCH = 1.05f
    /** Fade of the music on the title screen, at the start of a game and after game over. */
    val MUSIC_FADE: Duration = 1.seconds
    /** Music volume while the game is paused; it plays at full volume otherwise. */
    const val PAUSED_MUSIC_VOLUME = 0.2f

    /** Seed of the random source in autopilot mode, so smoke runs repeat. */
    const val AUTOPILOT_SEED = 42
}
