package klyph.scene

import klyph.app.App
import klyph.app.FixedStepper
import klyph.app.Phase
import klyph.app.Schedule
import klyph.app.System
import klyph.app.Time
import klyph.app.applyFrameTransitions
import klyph.entity.Commands
import klyph.entity.Entity
import klyph.entity.World
import klyph.input.Input
import klyph.render.Camera
import kotlin.time.Duration

/** A handle no test world ever issues, so it is never alive; stands for "not spawned yet". */
internal val NO_ENTITY: Entity = Entity.of(1_000_000, 0)

/** A system whose work needs no [App], so the harness can run it headless. */
internal class TestSystem(val action: () -> Unit) : System {
    override fun run(app: App) = action()

    override fun toString(): String = "TestSystem"
}

/**
 * Drives a [Schedule] and a [SceneStack] the way the frame loop does, without an [App], which needs
 * a window. Scene actions are registered per scene with [scene] and logged as `enter <name>` and
 * `exit <name>` before they run.
 */
internal class SceneHarness : SceneHost {
    override val world: World = World()
    override val commands: Commands = Commands(world)
    val schedule = Schedule()
    val stack = SceneStack()
    val log = mutableListOf<String>()

    /** Stands for `App.input`, which [frame] finishes through [camera] before INPUT as the frame loop does. */
    val input = Input()

    /** Stands for `App.camera`, whose offset [frame] clears when it applies a transition, as the frame loop does. */
    val camera = Camera(viewportWidth = 800f, viewportHeight = 600f)

    /** The frame clock [frame] with a duration drives, at the fixed rate of [FIXED_RATE] steps a second. */
    val time = Time(FIXED_RATE)
    private val stepper = FixedStepper(time.fixedDelta, maxStepsPerFrame = 5)
    private val enterActions = HashMap<Scene, () -> Unit>()
    private val exitActions = HashMap<Scene, () -> Unit>()

    fun scene(name: String, enter: () -> Unit = {}, exit: () -> Unit = {}, build: SceneBuilder.() -> Unit = {}): Scene {
        val scene = buildScene(name, build)
        enterActions[scene] = enter
        exitActions[scene] = exit
        return scene
    }

    fun system(action: () -> Unit): System = TestSystem(action)

    fun addSystem(phase: Phase, action: () -> Unit) {
        schedule.add(phase, TestSystem(action))
    }

    override fun enter(scene: Scene) {
        log += "enter ${scene.name}"
        enterActions.getValue(scene)()
    }

    override fun exit(scene: Scene) {
        log += "exit ${scene.name}"
        exitActions.getValue(scene)()
    }

    /** Runs [phase] as `App.run` does. */
    fun run(phase: Phase) {
        schedule.run(phase, world, commands, stack.currentOrNull, stack.currentOwner) { (it as TestSystem).action() }
    }

    /**
     * Runs each phase of a frame once, with no frame clock, finishing the input before INPUT, and
     * then applies the queued transitions as the frame loop does.
     */
    fun frame() {
        input.endFrame(camera)
        for (phase in FRAME_PHASES) run(phase)
        stack.applyFrameTransitions(this, camera)
    }

    /**
     * Runs one frame that lasted [delta] in the order `FrameLoop.frame` does: starts the frame on
     * [time], advances the clock of the top scene, finishes the input, runs INPUT, one FIXED_UPDATE
     * for every fixed step [delta] covers, UPDATE, LATE_UPDATE and RENDER, applies the queued
     * transitions, clearing the camera's offset when any is queued, and ends the frame. Returns the
     * number of fixed steps.
     */
    fun frame(delta: Duration): Int {
        time.beginFrame(delta)
        stack.advanceClock(time.delta)
        input.endFrame(camera)
        run(Phase.INPUT)
        val steps = stepper.accumulate(time.delta)
        repeat(steps) {
            time.beginFixedStep()
            run(Phase.FIXED_UPDATE)
            time.endFixedStep()
        }
        run(Phase.UPDATE)
        run(Phase.LATE_UPDATE)
        run(Phase.RENDER)
        stack.applyFrameTransitions(this, camera)
        time.endFrame()
        return steps
    }

    /** Applies the queued transitions, as the loop does after STARTUP and at the end of a frame. */
    fun applyTransitions() {
        stack.applyTransitions(this)
    }

    /** Exits every scene and runs SHUTDOWN, as the frame loop does after the last frame. */
    fun shutdown() {
        stack.exitEveryScene(this)
        run(Phase.SHUTDOWN)
    }

    override fun toString(): String = "SceneHarness(stack=$stack)"

    private companion object {
        const val FIXED_RATE = 60
        val FRAME_PHASES = listOf(Phase.INPUT, Phase.FIXED_UPDATE, Phase.UPDATE, Phase.LATE_UPDATE, Phase.RENDER)
    }
}
