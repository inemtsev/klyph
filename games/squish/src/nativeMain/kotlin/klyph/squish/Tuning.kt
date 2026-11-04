package klyph.squish

import klyph.platform.Color
import klyph.platform.withAlpha
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Every tuning value of the game in one place. Distances are world units, times are seconds unless
 * they are a [Duration], and angles are radians.
 */
object Tuning {
    const val WINDOW_WIDTH = 1280
    const val WINDOW_HEIGHT = 720

    /** The world is one screen: the camera fits this area into the window, whatever its size. */
    const val WORLD_WIDTH = 1280f
    const val WORLD_HEIGHT = 720f

    val GRASS_COLOR = Color(red = 0.40f, green = 0.70f, blue = 0.32f)

    // The picnic blanket, a red gingham drawn with rectangles.
    const val BLANKET_X = 100f
    const val BLANKET_Y = 90f
    const val BLANKET_WIDTH = 1080f
    const val BLANKET_HEIGHT = 540f
    const val BLANKET_CHECK = 60f
    const val BLANKET_SHADOW_OFFSET = 10f
    val BLANKET_COLOR = Color(red = 1f, green = 0.97f, blue = 0.9f)
    val BLANKET_STRIPE_COLOR = Color(red = 0.9f, green = 0.2f, blue = 0.22f, alpha = 0.45f)
    val BLANKET_SHADOW_COLOR = Color(red = 0.1f, green = 0.3f, blue = 0.1f, alpha = 0.3f)

    // Draw order of world sprites and rectangles.
    const val LAYER_BLANKET = -10
    const val LAYER_SPLATS = 1
    const val LAYER_FOOD = 2
    const val LAYER_BUGS = 3
    const val LAYER_EFFECTS = 4
    const val LAYER_CURSOR = 100

    /** How long a round lasts unless the bugs finish the food first. */
    const val ROUND_SECONDS = 60f

    const val CAKE_X = 640f
    const val CAKE_Y = 360f
    const val CAKE_SIZE = 190f
    const val CAKE_RADIUS = 80f
    const val CAKE_BITES = 14

    const val MELON_LEFT_X = 400f
    const val MELON_RIGHT_X = 880f
    const val MELON_Y = 400f
    const val MELON_SIZE = 150f
    const val MELON_RADIUS = 50f
    const val MELON_BITES = 8
    /** Tilt of each watermelon slice in radians, mirrored on the right one. */
    const val MELON_TILT = 0.3f

    /** A food item shrinks to this share of its size as its last bite goes. */
    const val FOOD_MIN_SCALE = 0.45f

    /** Seconds before the first bug of a round. */
    const val FIRST_SPAWN_DELAY = 1f
    /** Seconds between bugs at the start and at the end of a round; the gap shrinks steadily. */
    const val SPAWN_INTERVAL_START = 1.4f
    const val SPAWN_INTERVAL_END = 0.45f
    /** Bug speed factor at the start and at the end of a round. */
    const val SPEED_FACTOR_START = 1f
    const val SPEED_FACTOR_END = 1.8f
    const val MAX_BUGS = 16
    /** Bugs appear this far outside the edge of the world. */
    const val SPAWN_MARGIN = 50f
    /** A bug that has eaten is removed once this far outside the world. */
    const val EXIT_MARGIN = 80f
    /** A bug that has eaten runs off this much faster than it came. */
    const val FULL_SPEED_FACTOR = 1.4f

    /** Side-to-side swing of a walking bug, in radians either way, and its rate in radians per second. */
    const val WIGGLE_ANGLE = 0.35f
    const val WIGGLE_RATE = 9f
    /** Width change of a walking bug's sprite at the height of its waddle. */
    const val WADDLE_SCALE = 0.05f

    /** Radius of the swatter's head: every bug whose body it touches is squished. */
    const val SWAT_RADIUS = 44f
    /** Seconds the swatter shows its slap after each swat. */
    const val SWAT_SLAP_SECONDS = 0.15f
    const val SWAT_SLAP_SCALE = 0.82f
    const val SWAT_SLAP_TILT = -0.3f
    /** Side of the swatter cursor sprite. */
    const val CURSOR_SIZE = 150f
    /** Where the middle of the swatter's head is, as a share of the sprite from its top-left corner. */
    const val CURSOR_ORIGIN_X = 0.46f
    const val CURSOR_ORIGIN_Y = 0.23f
    /** Cursor speed with a gamepad stick or the arrow keys at full tilt. */
    const val CURSOR_SPEED = 800f

    // The squish: a splat on the blanket, the flattened bug, droplets and a "+1".
    const val SPLAT_TEXELS = 96
    const val SPLAT_SCALE = 1.3f
    val SPLAT_HOLD: Duration = 2.seconds
    val SPLAT_FADE: Duration = 2500.milliseconds
    const val SPLAT_ALPHA = 0.85f
    const val SQUASHED_WIDTH_SCALE = 1.3f
    const val SQUASHED_LENGTH_SCALE = 0.45f
    val SQUASHED_FADE: Duration = 1200.milliseconds
    val SQUASHED_TINT = Color(red = 0.7f, green = 0.7f, blue = 0.7f)
    const val DROPLET_TEXELS = 16
    const val DROPLETS = 18
    const val DROPLET_MIN_SPEED = 120f
    const val DROPLET_MAX_SPEED = 320f
    val DROPLET_MIN_LIFETIME: Duration = 250.milliseconds
    val DROPLET_MAX_LIFETIME: Duration = 550.milliseconds
    const val DROPLET_START_SCALE = 1.2f
    const val DROPLET_END_SCALE = 0.3f
    const val POPUP_SCALE = 3f
    const val POPUP_RISE = 60f
    val POPUP_FADE: Duration = 800.milliseconds
    val POPUP_COLOR = Color(red = 1f, green = 0.92f, blue = 0.2f)

    // Crumbs when a bug takes a bite.
    const val CRUMBS = 10
    const val CRUMBS_LAST_BITE = 30
    const val CRUMB_MIN_SPEED = 60f
    const val CRUMB_MAX_SPEED = 180f
    val CRUMB_MIN_LIFETIME: Duration = 300.milliseconds
    val CRUMB_MAX_LIFETIME: Duration = 600.milliseconds

    // Screen text, in points from the window's top-left corner, with the built-in 8x8 font.
    const val TITLE_TEXT_SCALE = 8f
    const val BIG_TEXT_SCALE = 5f
    const val TEXT_SCALE = 3f
    const val SMALL_TEXT_SCALE = 2f
    const val LINE_SPACING = 40f
    const val HUD_MARGIN = 20f
    const val TEXT_SHADOW_OFFSET = 3f
    val TEXT_SHADOW_COLOR = Color(red = 0.15f, green = 0.1f, blue = 0.2f, alpha = 0.8f)
    val HINT_COLOR = Color(red = 1f, green = 0.95f, blue = 0.75f)
    val TITLE_COLOR = Color(red = 1f, green = 0.85f, blue = 0.1f)
    /** Side of each ladybug medal on the end screen. */
    const val MEDAL_SIZE = 72f
    /** Squished bugs needed for the second and the third medal on the end screen; one bug earns the first. */
    const val TWO_MEDALS = 15
    const val THREE_MEDALS = 30
    const val TIME_BAR_WIDTH = 360f
    const val TIME_BAR_HEIGHT = 22f
    val TIME_BAR_BACK = Color.BLACK.withAlpha(0.35f)
    val TIME_BAR_FILL = Color(red = 1f, green = 0.85f, blue = 0.2f)
    const val FOOD_ICON_SIZE = 56f
    val EATEN_FOOD_TINT = Color(red = 0.3f, green = 0.3f, blue = 0.3f, alpha = 0.5f)
    val PAUSE_SHADE = Color.BLACK.withAlpha(0.45f)
    val END_SHADE = Color.BLACK.withAlpha(0.4f)
    /** Seconds the end screen ignores presses, so a child still tapping does not skip it. */
    val END_INPUT_DELAY: Duration = 800.milliseconds

    // Title screen bugs walk round the food on flat ellipses between the title and the prompt.
    const val TITLE_BUGS = 5
    const val TITLE_ORBIT_MIN = 200f
    const val TITLE_ORBIT_MAX = 240f
    /** Horizontal and vertical radius of a title bug's ellipse, as factors of its size. */
    const val TITLE_ORBIT_WIDTH = 1.6f
    const val TITLE_ORBIT_HEIGHT = 0.7f

    const val SQUISH_MIN_PITCH = 0.9f
    const val SQUISH_MAX_PITCH = 1.2f
    const val MUNCH_VOLUME = 0.7f
    val MUSIC_FADE: Duration = 1.seconds
    const val PAUSED_MUSIC_VOLUME = 0.2f
    const val END_MUSIC_VOLUME = 0.4f

    /** Seed of the random source in autopilot mode, so smoke runs repeat. */
    const val AUTOPILOT_SEED = 7
    /** Cursor speed of the autopilot, and the gap between its swats. */
    const val AUTOPILOT_CURSOR_SPEED = 900f
    const val AUTOPILOT_SWAT_INTERVAL = 0.4f
    /** The autopilot swats once the cursor is this close to its bug. */
    const val AUTOPILOT_AIM_DISTANCE = 16f
    /** Seconds the end screen waits before the autopilot plays again. */
    val AUTOPILOT_RETRY_DELAY: Duration = 1500.milliseconds
}
