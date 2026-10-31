package klyph.app

import klyph.entity.World
import klyph.event.Events
import klyph.platform.Color
import klyph.platform.PresentMode
import klyph.platform.Window
import klyph.render.Camera

/**
 * Represents a running klyph application: the world, the event bus, the clock, the assets, the
 * camera and the systems the frame loop runs. [runApp] creates the one instance and passes it to its `setup` block and to
 * every [System].
 *
 * An `App` is only valid inside [runApp]. Once [runApp] returns, its window, GPU device and
 * assets are closed, so keeping a reference and using it afterwards is an error.
 *
 * Not thread-safe; call it from systems only.
 *
 * Samples live in the sandbox because they need a display.
 */
public class App internal constructor(
    /** The settings [runApp] was called with. */
    public val config: AppConfig,
    /**
     * The window frames are presented to. Its size is `window.width` by `window.height` points and
     * its drawable size `window.pixelWidth` by `window.pixelHeight` pixels.
     */
    public val window: Window,
    /**
     * The present mode in effect. It may differ from [AppConfig.presentMode] when the requested
     * mode is unsupported, in which case the app keeps [PresentMode.VSYNC].
     */
    public val presentMode: PresentMode,
    /**
     * Loads the textures, images and sprite sheets the app draws, from [AppConfig.assetRoot] or the
     * `assets/` directory next to the executable. The engine closes it, and every texture it
     * created, after the last [Phase.SHUTDOWN] system.
     */
    public val assets: Assets,
    /**
     * The camera sprites are drawn through. It starts with a zoom equal to the window's
     * [Window.pixelDensity], so one world unit is one logical point (the unit the window size is
     * given in, two pixels across on a Retina display), and centered on the middle of the window, so
     * world (0, 0) is the window's top-left corner. Its zoom is therefore the density times the
     * game's own zoom: to show the world twice as large, set it to `2 * window.pixelDensity`. When
     * the density changes, the engine scales the zoom by the same ratio.
     *
     * The engine keeps its viewport equal to the window's pixel size and never moves it; move, zoom
     * or rotate it from a system, such as a [Phase.LATE_UPDATE] system that follows the player.
     */
    public val camera: Camera,
) {
    private val schedule = Schedule()
    private var startupDone = false
    internal var isQuitRequested: Boolean = false
        private set

    /** The entities and components every system works on. */
    public val world: World = World()

    /** The event bus. The engine publishes `klyph.event` events into it during [Phase.INPUT]. */
    public val events: Events = Events()

    /** The frame clock. [Time.delta] equals [Time.fixedDelta] inside [Phase.FIXED_UPDATE]. */
    public val time: Time = Time(config.fixedRate)

    /** The timings of the last completed frame. */
    public val stats: FrameStats = FrameStats()

    /** The color the next frames are cleared to. Starts as [AppConfig.clearColor]. */
    public var clearColor: Color = config.clearColor

    /**
     * Adds [system] to [phase]. It runs each time [phase] runs, after the systems added to [phase]
     * before it, and after the engine's own work in [Phase.INPUT] and [Phase.LATE_UPDATE] and before
     * it in [Phase.RENDER].
     *
     * Systems may be added from the `setup` block of [runApp] or from another system. A system
     * added to the running phase cannot run in it, and a [Phase.STARTUP] system added after
     * startup would never run, so both are rejected.
     *
     * @throws IllegalStateException if [phase] is running, or if [phase] is [Phase.STARTUP] and
     *   startup has already run.
     */
    public fun addSystem(phase: Phase, system: System) {
        check(phase != Phase.STARTUP || !startupDone) { "Cannot add a STARTUP system after startup has run." }
        schedule.add(phase, system)
    }

    /**
     * Ends the loop after the current frame. The rest of the frame, including rendering, still
     * runs, then every [Phase.SHUTDOWN] system runs and [runApp] returns. Called from
     * [Phase.STARTUP], it ends the app before the first frame. Calling it again has no effect.
     *
     * The engine calls it when the platform reports `QuitRequested`, such as the window's close
     * button.
     */
    public fun quit() {
        isQuitRequested = true
    }

    /** Runs the systems of [phase]. */
    internal fun run(phase: Phase) {
        schedule.run(phase, this)
        if (phase == Phase.STARTUP) startupDone = true
    }

    /** Returns `App(title=klyph, frame=12, entities=100)`. The format may change. */
    override fun toString(): String = "App(title=${config.title}, frame=${time.frame}, entities=${world.entityCount})"
}
