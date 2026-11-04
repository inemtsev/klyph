package klyph.roller

import klyph.platform.Color
import klyph.platform.withAlpha
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Every tuning value of the game in one place. Distances are world units, which are points at
 * the window's own size, speeds are units per second, times are seconds unless they are a
 * [Duration], and angles are radians. y points down, so a jump has a negative vertical speed.
 */
object Tuning {
    const val WINDOW_WIDTH = 960
    const val WINDOW_HEIGHT = 540

    /** The level is this tall; the camera zooms so that it fills the window's height. */
    const val WORLD_HEIGHT = 540f

    /** Falling this far below the world's bottom edge counts as falling into a gap. */
    const val PIT_MARGIN = 60f

    // Draw order of world sprites and canvas items, lowest first.
    const val LAYER_SKY = -100
    const val LAYER_FAR_CLOUDS = -90
    const val LAYER_SKYLINE = -80
    const val LAYER_NEAR_CLOUDS = -70
    const val LAYER_PYLONS = -30
    const val LAYER_BILLBOARDS = -29
    const val LAYER_POSTERS = -28
    const val LAYER_BUILDINGS = -10
    const val LAYER_DECOR = -5
    const val LAYER_PICKUPS = 0
    const val LAYER_ENEMIES = 5
    const val LAYER_SQUASHED = 6
    const val LAYER_PLAYER = 10
    const val LAYER_EFFECTS = 20

    // The hero. The box is what touches the roofs; the sprite is drawn a little wider than it.
    const val PLAYER_WIDTH = 26f
    const val PLAYER_HEIGHT = 50f
    const val PLAYER_SPRITE_HEIGHT = 64f
    const val PLAYER_START_X = 120f

    /** Top speed on the blades, and how fast it is reached on a roof and in the air. */
    const val MAX_SPEED = 330f
    const val GROUND_ACCELERATION = 700f
    const val AIR_ACCELERATION = 520f
    /** Braking while pushing against the way the hero rolls. */
    const val TURN_DECELERATION = 1500f
    /** Slowing down on a roof with nothing pressed: the blades glide a long way. */
    const val GLIDE_FRICTION = 240f
    const val AIR_DRAG = 60f

    const val GRAVITY = 1500f
    const val JUMP_SPEED = 620f
    /** Gravity is this much stronger while rising with the jump button up, which cuts a jump short. */
    const val SHORT_HOP_GRAVITY = 3f
    const val MAX_FALL_SPEED = 900f
    /** A jump still works this long after rolling off an edge. */
    const val COYOTE_SECONDS = 0.1f
    /** A jump pressed this long before landing happens on landing. */
    const val JUMP_BUFFER_SECONDS = 0.12f

    /** Vertical speed after stomping an enemy, more with the jump button held. */
    const val STOMP_BOUNCE = 420f
    const val STOMP_BOUNCE_HELD = 600f
    /** A stomp counts while the hero's feet are at most this far below the enemy's top. */
    const val STOMP_TOLERANCE = 14f

    const val MAX_HEARTS = 3
    const val KNOCKBACK_X = 280f
    const val KNOCKBACK_Y = 330f
    /** Seconds without control after a hit, and of blinking invulnerability. */
    const val HURT_LOCK_SECONDS = 0.3f
    const val INVULNERABLE_SECONDS = 1.2f
    const val BLINK_SECONDS = 0.07f
    /** After falling into a gap the hero is put back this far behind where he left the roof. */
    const val RESPAWN_BACKSET = 80f
    const val RESPAWN_EDGE_MARGIN = 40f

    /** Units rolled per skating stride, which swaps the two skating frames. */
    const val STRIDE_LENGTH = 46f
    /** Lean of the sprite at top speed, in radians. */
    const val LEAN = 0.08f

    const val CAMERA_SMOOTHING = 8f
    /** The camera looks this far ahead of the hero in the way he faces. */
    const val CAMERA_LOOK_AHEAD = 140f
    const val CAMERA_LOOK_AHEAD_SMOOTHING = 2.5f
    const val CAMERA_SHAKE_SECONDS = 0.25f
    const val CAMERA_SHAKE = 5f

    // Enemies.
    const val BLOB_WIDTH = 48f
    const val BLOB_HEIGHT = 36f
    const val BLOB_SPEED = 55f
    const val BLOB_WOBBLE_RATE = 9f
    const val BLOB_WOBBLE = 0.07f
    const val HELI_WIDTH = 58f
    const val HELI_HEIGHT = 54f
    const val HELI_SPEED = 70f
    const val HELI_BOB = 26f
    const val HELI_BOB_RATE = 2.4f
    const val STOMP_POINTS = 150

    // Pickups.
    const val RING_SIZE = 24f
    const val RING_RADIUS = 12f
    const val RING_POINTS = 10
    const val RING_SPIN_RATE = 3.2f
    const val HEART_SIZE = 30f
    const val HEART_BOB = 4f
    /** Points for a heart picked up with every heart already full. */
    const val HEART_POINTS = 100

    // Level complete bonuses.
    const val HEART_BONUS = 500
    /** Ten points for every second under par. */
    const val PAR_SECONDS = 60f
    const val TIME_BONUS_PER_SECOND = 10
    /** Seconds the tally takes to count the total up. */
    const val TALLY_SECONDS = 1.5f

    // Effects.
    const val POPUP_SCALE = 2f
    const val POPUP_RISE = 50f
    val POPUP_FADE: Duration = 700.milliseconds
    val POPUP_DELAY: Duration = 250.milliseconds
    val POPUP_COLOR = Color.WHITE
    val STAR_LIFETIME: Duration = 180.milliseconds
    const val STAR_SIZE = 30f
    val SQUASHED_HOLD: Duration = 600.milliseconds
    val SQUASHED_FADE: Duration = 500.milliseconds
    const val SQUASHED_SCALE_Y = 0.3f
    const val SQUASHED_SCALE_X = 1.3f
    val FALLING_LIFETIME: Duration = 1500.milliseconds
    const val FALLING_SPIN = 9f
    const val DUST_LANDING_SPEED = 380f

    // Backdrop. Parallax factors: 0 stays with the camera, 1 moves with the world.
    const val FAR_CLOUD_PARALLAX = 0.1f
    const val SKYLINE_PARALLAX = 0.25f
    const val NEAR_CLOUD_PARALLAX = 0.4f
    /** Clouds repeat every this many units of their own layer. */
    const val CLOUD_PERIOD = 2400f
    /** The skyline is painted this many texels across and drawn this many points per texel. */
    const val SKYLINE_TEXELS_WIDE = 320
    const val SKYLINE_TEXELS_HIGH = 80
    const val SKYLINE_SCALE = 3f
    val SKY_TOP = Color(0x8C93E0FFu)
    val SKY_BOTTOM = Color(0xE4DDF5FFu)

    // Buildings and ledges are painted at this many points per texel, for chunky pixels.
    const val PIXEL = 2f
    const val LEDGE_HEIGHT = 16f

    // Screen text, in points from the window's top-left corner, with the built-in 8x8 font.
    const val TITLE_TEXT_SCALE = 7f
    const val BIG_TEXT_SCALE = 5f
    const val TEXT_SCALE = 3f
    const val SMALL_TEXT_SCALE = 2f
    const val LINE_SPACING = 36f
    const val HUD_MARGIN = 18f
    const val HUD_HEART_SIZE = 30f
    const val HUD_RING_SIZE = 26f
    const val TEXT_SHADOW_OFFSET = 3f
    val TEXT_SHADOW_COLOR = Color(0x2A1840CCu)
    val HINT_COLOR = Color(0xFFF3C0FFu)
    val TITLE_COLOR = Color(0xFFD23AFFu)
    val SCORE_COLOR = Color.WHITE
    val LOST_HEART_TINT = Color(0x3A2A4A90u)
    val PAUSE_SHADE = Color.BLACK.withAlpha(0.45f)
    val END_SHADE = Color(0x1A1030A0u)
    /** Seconds the end screens ignore presses, so a jump still held does not skip them. */
    val END_INPUT_DELAY: Duration = 900.milliseconds
    /** Units per second the title screen's camera drifts along the level. */
    const val TITLE_PAN_SPEED = 60f

    val MUSIC_FADE: Duration = 1.seconds
    const val PAUSED_MUSIC_VOLUME = 0.25f
    const val END_MUSIC_VOLUME = 0.35f
    const val MUSIC_VOLUME = 0.7f

    /** Seed of the random source in autopilot mode, so smoke runs repeat. */
    const val AUTOPILOT_SEED = 11
    /** The autopilot jumps when the roof ends within this many seconds of rolling at its speed, plus a margin. */
    const val AUTOPILOT_EDGE_SECONDS = 0.06f
    const val AUTOPILOT_EDGE_MARGIN = 14f
    /** Seconds the end screens wait before the autopilot plays again. */
    val AUTOPILOT_RETRY_DELAY: Duration = 2500.milliseconds
}
