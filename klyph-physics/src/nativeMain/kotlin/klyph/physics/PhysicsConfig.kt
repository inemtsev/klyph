package klyph.physics

import klyph.math.Vec2
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How a [PhysicsWorld] moves time. Every value is in world units and seconds, so a world behaves the
 * same however finely it is stepped, except where a value says otherwise. Pass the values by name.
 *
 * Two configs are equal when every value is. [toString] gives every value in constructor order, as
 * `PhysicsConfig(gravity=Vec2(0.0, 1500.0), substeps=4, ...)`; the format may change.
 *
 * @throws IllegalArgumentException if a value is outside the range its description gives.
 *
 * @sample samples.physics.WorldSamples.physicsConfig
 */
public class PhysicsConfig(
    /** Acceleration on every particle and body, in units per second squared. y grows down, so down is positive. */
    public val gravity: Vec2 = Vec2(0f, 1500f),
    /** Number of substeps each [PhysicsWorld.step] is cut into, at least 1. More is stiffer and steadier, and costs more. */
    public val substeps: Int = 4,
    /** Number of constraint passes per substep, at least 1, each followed by collision, so walls have the last word. */
    public val iterations: Int = 6,
    /**
     * How fast motion dies away, per second, finite and not negative: velocity is multiplied by
     * `exp(-damping * h)` every substep of `h` seconds, so the same damping loses the same share of
     * speed per second however many substeps there are. 0 keeps all of it; 0.24 loses about a fifth
     * per second.
     */
    public val damping: Float = 0.24f,
    /**
     * The farthest a particle may move in one substep, in units, more than 0. Keep it under half the
     * thinnest wall, or a fast particle can pop out the far side. This one is per substep on purpose:
     * walls do not get thicker when you add substeps.
     */
    public val maxStep: Float = 4f,
    /**
     * Side of a cell of the broadphases that find which things might touch, in units, or
     * [Broadphase.AUTO], the default, to size the cells at every step to what is in them. About the
     * size of a body works best.
     */
    public val cellSize: Float = Broadphase.AUTO,
    /**
     * Slower than this into a surface, in units per second, nothing bounces, whatever its restitution:
     * resting things settle instead of buzzing. Gravity alone reaches it in a fall of under a unit.
     */
    public val bounceSpeed: Float = 40f,
    /**
     * How long a rigid body and everything it touches must be still before they fall asleep and stop
     * costing time, more than zero; [Duration.INFINITE] for never.
     */
    public val timeToSleep: Duration = 500.milliseconds,
    /**
     * Slower than this, in units per second, a rigid body counts as still: its speed, plus how fast
     * its farthest point goes round as it turns.
     */
    public val sleepSpeed: Float = 5f,
) {
    init {
        require(substeps >= 1) { "Substeps $substeps is less than 1." }
        require(iterations >= 1) { "Iterations $iterations is less than 1." }
        require(damping >= 0f && damping.isFinite()) { "Damping $damping is negative or not finite." }
        require(maxStep > 0f) { "Max step $maxStep is not more than 0." }
        require(cellSize >= 0f && cellSize.isFinite()) { "Cell size $cellSize is neither more than 0 nor Broadphase.AUTO." }
        require(bounceSpeed >= 0f) { "Bounce speed $bounceSpeed is negative." }
        require(timeToSleep.isPositive()) { "Time to sleep $timeToSleep is not more than zero." }
        require(sleepSpeed >= 0f && sleepSpeed.isFinite()) { "Sleep speed $sleepSpeed is negative or not finite." }
    }

    /** [timeToSleep] in seconds, for the step's arithmetic: infinity for never. */
    internal val timeToSleepSeconds: Float =
        if (timeToSleep.isInfinite()) Float.POSITIVE_INFINITY else timeToSleep.inWholeNanoseconds / 1e9f

    override fun equals(other: Any?): Boolean =
        other is PhysicsConfig && gravity == other.gravity && substeps == other.substeps && iterations == other.iterations &&
            damping.equals(other.damping) && maxStep.equals(other.maxStep) && cellSize.equals(other.cellSize) &&
            bounceSpeed.equals(other.bounceSpeed) && timeToSleep == other.timeToSleep && sleepSpeed.equals(other.sleepSpeed)

    override fun hashCode(): Int {
        var result = gravity.hashCode()
        result = 31 * result + substeps
        result = 31 * result + iterations
        result = 31 * result + damping.hashCode()
        result = 31 * result + maxStep.hashCode()
        result = 31 * result + cellSize.hashCode()
        result = 31 * result + bounceSpeed.hashCode()
        result = 31 * result + timeToSleep.hashCode()
        result = 31 * result + sleepSpeed.hashCode()
        return result
    }

    override fun toString(): String =
        "PhysicsConfig(gravity=$gravity, substeps=$substeps, iterations=$iterations, damping=$damping, maxStep=$maxStep, " +
            "cellSize=$cellSize, bounceSpeed=$bounceSpeed, timeToSleep=$timeToSleep, sleepSpeed=$sleepSpeed)"
}
