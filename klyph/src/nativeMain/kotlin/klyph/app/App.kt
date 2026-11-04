package klyph.app

import klyph.audio.Audio
import klyph.collision.Collisions
import klyph.entity.Commands
import klyph.entity.World
import klyph.event.Events
import klyph.input.Input
import klyph.platform.Color
import klyph.platform.PresentMode
import klyph.platform.Window
import klyph.render.Camera
import klyph.render.Canvas
import klyph.scene.Scene
import klyph.scene.SceneHost
import klyph.scene.SceneStack

/**
 * Represents a running klyph application: the world, its command queue, the event bus, the input
 * state, the shared resources, the clock, the assets, the camera, the canvas, the audio, the
 * overlap detection, the debug overlay, the scene stack and the systems the frame loop runs.
 * [runApp] creates the one instance and passes it to its `setup` block and to
 * every [System].
 *
 * An `App` is only valid inside [runApp]. Once [runApp] returns, its window, GPU device, audio
 * and assets are closed, so keeping a reference and using it afterwards is an error.
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
     * its drawable size `window.pixelWidth` by `window.pixelHeight` pixels. The engine closes it;
     * do not call `close`.
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
     * The camera sprites are drawn through. It starts at zoom 1, so one world unit is one point
     * (the unit the window size is given in, two pixels across on a display of
     * [Window.pixelDensity] 2), on every display, and centered on the middle of the window, so
     * world (0, 0) is the window's top-left corner. To show the world twice as large, set its zoom
     * to 2. The renderer turns points into pixels, so a change of density leaves the zoom alone.
     *
     * The engine keeps its viewport equal to the window's size in points and never moves it; move,
     * zoom or rotate it from a system, such as a [Phase.LATE_UPDATE] system that follows the player.
     */
    public val camera: Camera,
    /**
     * Plays sound effects and music, mixed on the audio device's own thread. Load sounds and
     * tracks through [assets]. When [AppConfig.audio] turns audio off, or the engine cannot open
     * an audio device, `audio.isEnabled` is `false` and every call does nothing, so the game runs
     * the same without sound. The engine closes it after the last [Phase.SHUTDOWN] system.
     */
    public val audio: Audio,
) {
    private val schedule = Schedule()
    private var startupDone = false
    internal var isQuitRequested: Boolean = false
        private set

    /** The entities and components every system works on. */
    public val world: World = World()

    /**
     * Queues changes to [world] that a system makes while it iterates a query, such as spawning a
     * bullet or destroying a hit enemy.
     *
     * The engine applies the queue after every system of every phase, so the changes one system
     * queues are in the world when the next system runs, even within the same phase. It applies it
     * once more after the transform propagation that follows [Phase.LATE_UPDATE], and once after
     * the `setup` block of [runApp], so entities queued there exist when [Phase.STARTUP] runs. It
     * also applies it after every `onEnter` and `onExit` action of a scene in [scenes], and after
     * each engine step that destroys entities: the `Lifetime` and `Fade` step after
     * [Phase.UPDATE], the particle step and the sprite animation step. An exception thrown by a
     * system skips the application after it.
     */
    public val commands: Commands = Commands(world)

    /**
     * The event bus. The engine publishes `klyph.event` events into it during [Phase.INPUT], and
     * `klyph.collision.Overlap` events between [Phase.FIXED_UPDATE] and [Phase.UPDATE].
     */
    public val events: Events = Events()

    /**
     * Finds overlapping colliders. Once per frame, after the last [Phase.FIXED_UPDATE] step and
     * before [Phase.UPDATE], the engine propagates transforms and then calls [Collisions.detect],
     * which publishes an `Overlap` event into [events] for every overlapping pair; systems from
     * [Phase.UPDATE] on read them. Its shape queries read the positions of that detection, so they
     * are current from [Phase.UPDATE] to the end of the frame. Its cell size starts as
     * [AppConfig.collisionCellSize].
     */
    public val collisions: Collisions = Collisions(config.collisionCellSize)

    /**
     * The keyboard, mouse and gamepad state as of this frame. The engine updates it at the start of
     * [Phase.INPUT], before any system runs, and computes `input.mouse.worldPosition` through
     * [camera] as it stands then.
     */
    public val input: Input = Input()

    /**
     * Holds state that belongs to no entity, such as a score or the game's settings, one value per
     * class, so systems can share it without globals.
     */
    public val resources: Resources = Resources()

    /**
     * The stack of scenes, such as a title screen, a level and a pause menu over it. Only the scene
     * on top runs its systems, after the App-level systems of each phase; the scenes below it are
     * frozen, and their particle emitters, sprite animations, lifetimes, fades and colliders stand still. The
     * entities a scene spawns are destroyed when it leaves the stack. Changes queue and apply at the end of the
     * frame. It starts empty, and the app runs only its App-level systems while it is.
     */
    public val scenes: SceneStack = SceneStack()

    private val sceneHost = object : SceneHost {
        override val world: World get() = this@App.world
        override val commands: Commands get() = this@App.commands
        override fun enter(scene: Scene) = scene.onEnter(this@App)
        override fun exit(scene: Scene) = scene.onExit(this@App)
        override fun toString(): String = "SceneHost(app=${this@App})"
    }

    /** The frame clock. [Time.delta] equals [Time.fixedDelta] inside [Phase.FIXED_UPDATE]. */
    public val time: Time = Time(config.fixedRate)

    /** The timings of the last completed frame. */
    public val stats: FrameStats = FrameStats()

    /**
     * Queues sprites, rectangles and text to draw this frame without entities, in world space
     * through [camera] or in screen space in points from the window's top-left corner. Call it
     * from [Phase.RENDER] systems only; anywhere else it throws [IllegalStateException]. The
     * engine draws what was queued at the end of RENDER, with the world's sprites, and empties it.
     * `assets.defaultFont` draws text with no font file.
     */
    public val canvas: Canvas = Canvas(assets.whitePixel)

    /**
     * The debug overlay: frame rate, frame and phase times, counts and a frame-time graph in the
     * window's top-left corner. It starts as [AppConfig.overlay] says, hidden by default;
     * [AppConfig.overlayKey], F3 by default, shows and hides it, and setting `overlay.isVisible`
     * does the same from a system.
     */
    public val overlay: DebugOverlay = DebugOverlay().apply { isVisible = config.overlay == OverlayStart.VISIBLE }

    /** The color the next frames are cleared to. Starts as [AppConfig.clearColor]. */
    public var clearColor: Color = config.clearColor

    /**
     * Adds [system] to [phase]. It runs each time [phase] runs, after the systems added to [phase]
     * before it, and after the engine's own work in [Phase.INPUT] and [Phase.LATE_UPDATE] and before
     * it in [Phase.RENDER]. It runs whatever [scenes] holds, before the systems the top scene has
     * for [phase]. Entities it creates belong to no scene, even while a scene is on top.
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
     * The engine calls it when the platform reports `PlatformEvent.Quit` and publishes the `QuitRequested` event, such as the window's close
     * button, and once [AppConfig.frameLimit] frames have run or [Time.elapsed] has reached
     * [AppConfig.timeLimit].
     */
    public fun quit() {
        isQuitRequested = true
    }

    /** Runs the systems of [phase] and then the top scene's, applying [commands] after each one. */
    internal fun run(phase: Phase) {
        schedule.run(phase, this)
        if (phase == Phase.STARTUP) startupDone = true
    }

    /** Applies the transitions queued on [scenes]. */
    internal fun applySceneTransitions() {
        scenes.applyTransitions(sceneHost)
    }

    /** Makes every scene leave [scenes], top first, before [Phase.SHUTDOWN]. */
    internal fun exitScenes() {
        scenes.exitEveryScene(sceneHost)
    }

    /** Returns `App(title=klyph, frame=12, entities=100)`. The format may change. */
    override fun toString(): String = "App(title=${config.title}, frame=${time.frameCount}, entities=${world.entityCount})"
}
