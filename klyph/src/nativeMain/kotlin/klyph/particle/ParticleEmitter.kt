package klyph.particle

import klyph.entity.ComponentType
import klyph.platform.Color
import klyph.render.TextureRegion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A component that sends out small quads showing [region], each living for a while and moving in a
 * straight line under [gravityX] and [gravityY] while its color and size blend from their start to
 * their end values. Particles are kept in a pool of [capacity] slots inside the emitter, not as
 * entities, so thousands of them cost no more than the arrays they live in.
 *
 * Once a frame, after [klyph.app.Phase.UPDATE] and before [klyph.app.Phase.LATE_UPDATE], the
 * engine advances every emitter whose entity has a `Transform`: it ages and moves the live
 * particles, removes those that have reached their lifetime, and then emits new ones at the
 * entity's current world position, taken from its `WorldTransform` if it has one and from its
 * `Transform` otherwise. An entity in a hierarchy therefore emits where the last transform
 * propagation put it. New particles come from the bursts requested with [emit] since the last
 * advance and from [rate]. Once emitted, a particle moves in world space and no longer follows the
 * entity. The sprite pass draws every live particle of a visible emitter in world space, in
 * [layer], as one quad centred on the particle. An emitter on an entity without a `Transform` is
 * neither advanced nor drawn. Destroying the entity removes its particles at once, so to let a
 * burst fade out after its source is gone, put the emitter on an entity of its own.
 *
 * Each particle draws its direction, speed, lifetime, start rotation and angular velocity at random
 * from [random] when it is emitted. Its direction is [direction] plus up to half of [spread] either
 * side. Angles in this class are in degrees, unlike `Transform.rotation`: 0 points along +x and
 * positive angles turn clockwise on screen, because y points down. Speeds are in world units per
 * second and gravity in world units per second squared. At a particle's age `t`, as a fraction of
 * its lifetime from 0 to 1, its color is the linear blend of [startColor] and [endColor] at `t`,
 * channel by channel, alpha included, and its size is the region's size in texels times the blend
 * of [startScale] and [endScale] at `t`.
 *
 * At most [capacity] particles are alive at once; emission beyond that is dropped. [capacity] is
 * [maxParticles], which must be positive. Setting a property outside its documented range throws
 * [IllegalArgumentException].
 *
 * Advancing allocates nothing. Not thread-safe; use it from systems only.
 *
 * Has no sample because creating a [klyph.render.Texture] needs a GPU device, which samples cannot
 * open.
 *
 * @throws IllegalArgumentException if [maxParticles] is not positive.
 */
public class ParticleEmitter(
    /** The part of a texture every particle shows. Changing it changes live particles too. */
    public var region: TextureRegion,
    maxParticles: Int = 256,
) {
    init {
        require(maxParticles > 0) { "Max particles $maxParticles is not positive." }
    }

    /** The most particles alive at once: the `maxParticles` the emitter was created with. */
    public val capacity: Int = maxParticles

    /** Returns how many particles are alive. Bursts requested with [emit] count once the engine has emitted them. */
    public var count: Int = 0
        private set

    /**
     * Particles emitted per second, spread over frames with the fraction carried from one frame to
     * the next; 0, the default, emits only the bursts requested with [emit]. Must be finite and not
     * negative.
     */
    public var rate: Float = 0f
        set(value) {
            require(value >= 0f && value.isFinite()) { "Rate $value is not a finite number of at least zero." }
            field = value
        }

    /** Middle of the directions particles leave in, in degrees; 0 points along +x, 90 down the screen. Must be finite. */
    public var direction: Float = 0f
        set(value) {
            require(value.isFinite()) { "Direction $value is not finite." }
            field = value
        }

    /**
     * Width of the fan of directions particles leave in, in degrees, centred on [direction]; 360,
     * the default, sends them every way and 0 only along [direction]. Must be between 0 and 360.
     */
    public var spread: Float = 360f
        set(value) {
            require(value in 0f..360f) { "Spread $value is not between 0 and 360." }
            field = value
        }

    /** Range a particle's speed is drawn from, in world units per second. Must not be empty. */
    public var speed: ClosedFloatingPointRange<Float> = 50f..100f
        set(value) {
            requireRange(value, "Speed")
            field = value
            speedStart = value.start
            speedEnd = value.endInclusive
        }

    /**
     * Range a particle's lifetime is drawn from, in seconds. Must not be empty or start below 0; a
     * particle with a lifetime of 0 is drawn once and removed at the next advance.
     */
    public var lifetime: ClosedFloatingPointRange<Float> = 0.5f..1f
        set(value) {
            requireRange(value, "Lifetime")
            require(value.start >= 0f) { "Lifetime $value starts below zero." }
            field = value
            lifetimeStart = value.start
            lifetimeEnd = value.endInclusive
        }

    /** Range a particle's rotation at emission is drawn from, in degrees. Must not be empty. */
    public var startRotation: ClosedFloatingPointRange<Float> = 0f..0f
        set(value) {
            requireRange(value, "Start rotation")
            field = value
            rotationStart = value.start * RADIANS_PER_DEGREE
            rotationEnd = value.endInclusive * RADIANS_PER_DEGREE
        }

    /** Range a particle's turning speed is drawn from, in degrees per second. Must not be empty. */
    public var angularVelocity: ClosedFloatingPointRange<Float> = 0f..0f
        set(value) {
            requireRange(value, "Angular velocity")
            field = value
            spinStart = value.start * RADIANS_PER_DEGREE
            spinEnd = value.endInclusive * RADIANS_PER_DEGREE
        }

    /** Horizontal acceleration of every live particle, in world units per second squared. */
    public var gravityX: Float = 0f

    /** Vertical acceleration of every live particle, in world units per second squared; positive pulls down. */
    public var gravityY: Float = 0f

    /** Tint of a particle when it is emitted. */
    public var startColor: Color = Color.WHITE

    /**
     * Tint of a particle at the end of its lifetime. The default is white at zero alpha, so
     * particles fade out without darkening on the way.
     */
    public var endColor: Color = Color(0xFFFFFF00u)

    /** Size of a particle when it is emitted, as a factor of the region's size in texels. */
    public var startScale: Float = 1f

    /** Size of a particle at the end of its lifetime, as a factor of the region's size in texels. */
    public var endScale: Float = 1f

    /** Draw-order group of every particle; higher layers draw on top. Any `Int`, negative included. */
    public var layer: Int = 0

    /** Whether the particles are drawn. Hidden particles still move, age and get emitted. */
    public var isVisible: Boolean = true

    /** Source of every random draw at emission. Pass a seeded `Random(seed)` for runs that repeat exactly. */
    public var random: Random = Random.Default

    // Range ends as primitives: reading a ClosedFloatingPointRange<Float> boxes its Float ends.
    private var speedStart = 50f
    private var speedEnd = 100f
    private var lifetimeStart = 0.5f
    private var lifetimeEnd = 1f
    private var rotationStart = 0f
    private var rotationEnd = 0f
    private var spinStart = 0f
    private var spinEnd = 0f

    // The pool: particle i lives in slot i of every array, for i below count.
    internal val xs = FloatArray(maxParticles)
    internal val ys = FloatArray(maxParticles)
    internal val velocitiesX = FloatArray(maxParticles)
    internal val velocitiesY = FloatArray(maxParticles)
    internal val ages = FloatArray(maxParticles)
    internal val lifetimes = FloatArray(maxParticles)

    /** Rotation in radians. */
    internal val rotations = FloatArray(maxParticles)

    /** Angular velocity in radians per second. */
    internal val spins = FloatArray(maxParticles)

    /** Particles requested by [emit] and not yet emitted, at most [capacity]. */
    internal var pendingBurst: Int = 0
        private set

    /** Fraction of a particle that [rate] has built up and not yet emitted. */
    private var carry = 0f

    /**
     * Requests a burst of [count] particles, emitted at the entity's world position the next time
     * the engine advances the emitter, after [Phase.UPDATE][klyph.app.Phase.UPDATE]. Bursts
     * requested in one frame add up. The burst is clamped to the slots free at that moment, so with
     * a full pool nothing is emitted. [count] must not be negative.
     *
     * @throws IllegalArgumentException if [count] is negative.
     */
    public fun emit(count: Int) {
        require(count >= 0) { "Particle count $count is less than zero." }
        pendingBurst = (pendingBurst.toLong() + count).coerceAtMost(capacity.toLong()).toInt()
    }

    /** Removes every live particle and every burst not yet emitted. [rate] keeps emitting from the next advance. */
    public fun clear() {
        count = 0
        pendingBurst = 0
        carry = 0f
    }

    /**
     * Moves the emitter on by [delta] seconds with the entity at ([x], [y]): ages, moves and turns
     * every live particle, removes those that have lived their lifetime, then emits the pending
     * burst and what [rate] built up. Returns the number of live particles.
     */
    internal fun advance(delta: Float, x: Float, y: Float): Int {
        integrate(delta)
        carry += rate * delta
        val fromRate = carry.toInt()
        carry -= fromRate
        val wanted = pendingBurst.toLong() + fromRate
        pendingBurst = 0
        val free = capacity - count
        spawn(x, y, if (wanted < free) wanted.toInt() else free)
        return count
    }

    /** Ages, moves and turns every live particle by [delta] seconds and swap-removes the dead ones. */
    private fun integrate(delta: Float) {
        val xs = xs
        val ys = ys
        val velocitiesX = velocitiesX
        val velocitiesY = velocitiesY
        val ages = ages
        val lifetimes = lifetimes
        val rotations = rotations
        val spins = spins
        val ax = gravityX * delta
        val ay = gravityY * delta
        var live = count
        var i = 0
        while (i < live) {
            val age = ages[i] + delta
            if (age >= lifetimes[i]) {
                // Move the last live particle into this slot and look at the slot again.
                val last = --live
                xs[i] = xs[last]
                ys[i] = ys[last]
                velocitiesX[i] = velocitiesX[last]
                velocitiesY[i] = velocitiesY[last]
                ages[i] = ages[last]
                lifetimes[i] = lifetimes[last]
                rotations[i] = rotations[last]
                spins[i] = spins[last]
                continue
            }
            ages[i] = age
            val vx = velocitiesX[i] + ax
            val vy = velocitiesY[i] + ay
            velocitiesX[i] = vx
            velocitiesY[i] = vy
            xs[i] += vx * delta
            ys[i] += vy * delta
            rotations[i] += spins[i] * delta
            i++
        }
        count = live
    }

    /** Emits [amount] particles at ([x], [y]); the caller has checked that they fit. */
    private fun spawn(x: Float, y: Float, amount: Int) {
        if (amount <= 0) return
        val random = random
        val centre = direction * RADIANS_PER_DEGREE
        val fan = spread * RADIANS_PER_DEGREE
        for (slot in count until count + amount) {
            val angle = centre + (random.nextFloat() - 0.5f) * fan
            val speed = speedStart + (speedEnd - speedStart) * random.nextFloat()
            xs[slot] = x
            ys[slot] = y
            velocitiesX[slot] = cos(angle) * speed
            velocitiesY[slot] = sin(angle) * speed
            ages[slot] = 0f
            lifetimes[slot] = lifetimeStart + (lifetimeEnd - lifetimeStart) * random.nextFloat()
            rotations[slot] = rotationStart + (rotationEnd - rotationStart) * random.nextFloat()
            spins[slot] = spinStart + (spinEnd - spinStart) * random.nextFloat()
        }
        count += amount
    }

    private fun requireRange(range: ClosedFloatingPointRange<Float>, name: String) {
        require(!range.isEmpty() && range.start.isFinite() && range.endInclusive.isFinite()) {
            "$name range $range is empty or not finite."
        }
    }

    /**
     * Returns `ParticleEmitter(count=12/256, rate=30.0, layer=0, visible=true, region=TextureRegion(...))`.
     * The format may change.
     */
    override fun toString(): String =
        "ParticleEmitter(count=$count/$capacity, rate=$rate, layer=$layer, visible=$isVisible, region=$region)"

    /** Component key for [ParticleEmitter]. */
    public companion object : ComponentType<ParticleEmitter>("ParticleEmitter") {
        private const val RADIANS_PER_DEGREE: Float = (PI / 180.0).toFloat()
    }
}
