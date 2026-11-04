package klyph.roller

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

// The hero's movement: momentum on the blades, gravity, a jump with variable height, coyote
// time and a jump buffer, and landing on roofs and ledges. klyph has colliders and overlap events
// but no physics, no solid response and no platforms, and its collision queries read the last
// frame's snapshot, which a fixed step cannot use for ground checks, so the game resolves the hero
// against the level's rectangles itself. The step is a plain function of a Body, so the
// autopilot can run it ahead to see where a jump would land.

/**
 * The hero's moving state: [x] is the middle of the box and [feet] its bottom edge, in world
 * units; [vx] and [vy] are in units per second.
 */
class Body(var x: Float, var feet: Float) {
    var vx = 0f
    var vy = 0f

    /** Whether the feet rest on a roof or a ledge after the last step. */
    var isGrounded = false

    /** Index in [RooftopRun.solids] of what the feet rest on, or -1 in the air. */
    var groundIndex = -1

    /** Seconds left in which a jump still works after leaving the ground. */
    var coyote = 0f

    /** Seconds left in which a jump pressed early still happens on landing. */
    var jumpBuffer = 0f

    /** Where the box was before the last step, for drawing between steps and for stomps. */
    var previousX = x
    var previousFeet = feet

    /** Set by the last step: whether it started a jump, and whether it landed and how fast. */
    var hasJumped = false
    var hasLanded = false
    var landingSpeed = 0f

    /** Copies every field of [other] into this body. */
    fun copyFrom(other: Body) {
        x = other.x
        feet = other.feet
        vx = other.vx
        vy = other.vy
        isGrounded = other.isGrounded
        groundIndex = other.groundIndex
        coyote = other.coyote
        jumpBuffer = other.jumpBuffer
        previousX = other.previousX
        previousFeet = other.previousFeet
        hasJumped = false
        hasLanded = false
    }

    /** Puts the body at ([x], [feet]) at rest, as if it had always been there. */
    fun placeAt(x: Float, feet: Float) {
        this.x = x
        this.feet = feet
        previousX = x
        previousFeet = feet
        vx = 0f
        vy = 0f
        isGrounded = false
        groundIndex = -1
        coyote = 0f
        jumpBuffer = 0f
    }

    override fun toString(): String = "Body(x=$x, feet=$feet, v=$vx,$vy, grounded=$isGrounded on $groundIndex)"
}

/**
 * Advances the body by [dt] seconds against [solids]: [move] from -1 to 1 pushes it left or
 * right, [isJumpPressed] asks for a jump this step and [isJumpHeld] keeps a rising jump going.
 * Buildings are walls from the side and floors from above; ledges are floors from above only.
 */
fun Body.step(solids: List<Solid>, move: Float, isJumpPressed: Boolean, isJumpHeld: Boolean, dt: Float) {
    hasJumped = false
    hasLanded = false
    previousX = x
    previousFeet = feet

    // Rolling: push to speed up, push back to brake hard, let go to glide.
    if (move != 0f) {
        val isBraking = vx != 0f && sign(move) != sign(vx)
        val acceleration = when {
            isBraking -> Tuning.TURN_DECELERATION
            isGrounded -> Tuning.GROUND_ACCELERATION
            else -> Tuning.AIR_ACCELERATION
        }
        if (isBraking || abs(vx) < Tuning.MAX_SPEED) {
            vx = (vx + move * acceleration * dt).coerceIn(-Tuning.MAX_SPEED, Tuning.MAX_SPEED)
        } else {
            // Faster than the top speed after a knockback: ease back down to it.
            vx = approach(vx, sign(vx) * Tuning.MAX_SPEED, Tuning.GLIDE_FRICTION * dt)
        }
    } else {
        vx = approach(vx, 0f, (if (isGrounded) Tuning.GLIDE_FRICTION else Tuning.AIR_DRAG) * dt)
    }

    // Jumping, with a little grace either side of being on the ground.
    jumpBuffer = if (isJumpPressed) Tuning.JUMP_BUFFER_SECONDS else jumpBuffer - dt
    coyote = if (isGrounded) Tuning.COYOTE_SECONDS else coyote - dt
    if (jumpBuffer > 0f && coyote > 0f) {
        vy = -Tuning.JUMP_SPEED
        isGrounded = false
        groundIndex = -1
        coyote = 0f
        jumpBuffer = 0f
        hasJumped = true
    }
    val gravity = if (vy < 0f && !isJumpHeld) Tuning.GRAVITY * Tuning.SHORT_HOP_GRAVITY else Tuning.GRAVITY
    vy = min(vy + gravity * dt, Tuning.MAX_FALL_SPEED)

    // Across first, stopping at the side of any building the box runs into.
    val half = Tuning.PLAYER_WIDTH / 2
    x += vx * dt
    for (index in solids.indices) {
        val solid = solids[index]
        if (solid.isLedge || feet <= solid.top + 0.5f) continue
        if (x + half <= solid.left || x - half >= solid.right) continue
        x = if (previousX <= solid.left) solid.left - half else solid.right + half
        vx = 0f
    }

    // Then down, landing on the highest floor the feet crossed this step.
    val wasGrounded = isGrounded
    feet += vy * dt
    isGrounded = false
    groundIndex = -1
    if (vy >= 0f) {
        var landing = -1
        for (index in solids.indices) {
            val solid = solids[index]
            if (x + half <= solid.left || x - half >= solid.right) continue
            if (previousFeet > solid.top + 0.01f || feet < solid.top) continue
            if (landing < 0 || solid.top < solids[landing].top) landing = index
        }
        if (landing >= 0) {
            if (!wasGrounded) {
                hasLanded = true
                landingSpeed = vy
            }
            feet = solids[landing].top
            vy = 0f
            isGrounded = true
            groundIndex = landing
        }
    }
}

/**
 * Runs the body ahead from its current state, pressing [move] and holding jump, a jump pressed
 * on the first step if [jump] is `true`, and returns the index of the solid it lands on, or -1 if
 * it falls out of the world first. [scratch] is overwritten.
 */
fun Body.predictLanding(scratch: Body, solids: List<Solid>, move: Float, jump: Boolean, dt: Float): Int {
    scratch.copyFrom(this)
    // A grounded body would land at once; count only landings after it has been in the air.
    var hasLeftGround = !scratch.isGrounded
    for (stepIndex in 0 until PREDICTION_STEPS) {
        scratch.step(solids, move, isJumpPressed = jump && stepIndex == 0, isJumpHeld = true, dt)
        if (!scratch.isGrounded) hasLeftGround = true
        if (hasLeftGround && scratch.isGrounded) return scratch.groundIndex
        if (scratch.feet > Tuning.WORLD_HEIGHT + Tuning.PIT_MARGIN) return -1
    }
    return if (scratch.isGrounded) scratch.groundIndex else -1
}

/**
 * Runs the body ahead as [predictLanding] does, jumping on the first step, and returns how many
 * steps it takes for the falling feet to come down to [height], or -1 if they never do. [scratch]
 * then holds the body at that moment.
 */
fun Body.predictDescent(scratch: Body, solids: List<Solid>, height: Float, move: Float, dt: Float): Int {
    scratch.copyFrom(this)
    for (stepIndex in 0 until PREDICTION_STEPS) {
        scratch.step(solids, move, isJumpPressed = stepIndex == 0, isJumpHeld = true, dt)
        if (scratch.vy > 0f && scratch.feet >= height) return stepIndex + 1
        if (scratch.isGrounded && stepIndex > 0) return -1
    }
    return -1
}

/** Steps the autopilot looks ahead: two and a half seconds at 60 steps a second. */
private const val PREDICTION_STEPS = 150

/** Returns [value] moved toward [target] by at most [amount]. */
fun approach(value: Float, target: Float, amount: Float): Float =
    if (value < target) min(value + amount, target) else maxOf(value - amount, target)
