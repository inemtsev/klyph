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

    const val SHOT_VOLUME = 0.6f
    /** Each shot plays at a random pitch in this range, so rapid fire does not drone. */
    const val SHOT_MIN_PITCH = 0.95f
    const val SHOT_MAX_PITCH = 1.05f
    /** Fade of the music at the start of a game and after game over. */
    val MUSIC_FADE: Duration = 1.seconds
    /** Music volume while the game is paused; it plays at full volume otherwise. */
    const val PAUSED_MUSIC_VOLUME = 0.2f

    /** Seed of the random source in autopilot mode, so smoke runs repeat. */
    const val AUTOPILOT_SEED = 42
}
