package klyph.app

import klyph.entity.World
import klyph.event.Events
import klyph.platform.Color
import klyph.platform.GpuDevice
import klyph.platform.GpuTexture
import klyph.platform.PresentMode
import klyph.platform.Window

/**
 * Represents a running klyph application: the world, the event bus, the clock and the systems the
 * frame loop runs. [runApp] creates the one instance and passes it to its `setup` block and to
 * every [System].
 *
 * An `App` is only valid inside [runApp]. Once [runApp] returns, its window and device are
 * closed, so keeping a reference and using it afterwards is an error.
 *
 * Not thread-safe; call it from systems only.
 *
 * Samples live in the sandbox because they need a display.
 */
public class App internal constructor(
    /** The settings [runApp] was called with. */
    public val config: AppConfig,
    /** The window frames are presented to. Its drawable size is `window.pixelWidth` by `window.pixelHeight`. */
    public val window: Window,
    /**
     * The GPU device the engine renders with, for creating textures until M2 brings a texture API.
     * Release what you create from it, at the latest in a [Phase.SHUTDOWN] system.
     */
    public val device: GpuDevice,
    /**
     * The present mode in effect. It may differ from [AppConfig.presentMode] when the requested
     * mode is unsupported, in which case the app keeps [PresentMode.VSYNC].
     */
    public val presentMode: PresentMode,
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
     * The texture every sprite is drawn with. While it is `null`, frames are cleared but no
     * sprites are drawn. The app does not release it; release it in a [Phase.SHUTDOWN] system.
     *
     * Temporary: it goes away when M2 adds per-sprite textures.
     */
    public var spriteTexture: GpuTexture? = null

    /**
     * Adds [system] to [phase]. It runs each time [phase] runs, after the systems added to [phase]
     * before it, and after the engine's own work in [Phase.INPUT] and before it in [Phase.RENDER].
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
