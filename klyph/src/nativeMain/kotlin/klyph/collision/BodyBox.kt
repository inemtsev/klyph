package klyph.collision

import klyph.entity.Transform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// What the solids that moved since the last step do to a body before its own move: its floor
// carries it, and the others push it along their move or crush it against another solid. The
// class description of KinematicBody gives the rules; SolidGrid.refresh records the moves.

/**
 * A body's box during its move: centered on [transform]'s position plus ([offsetX], [offsetY]),
 * [halfWidth] and [halfHeight] from its center to its edges, stopping at the solids in [mask] and
 * ignoring the one-way solids at or above [dropLine]. Moving the transform moves the box. The grid
 * keeps one and [set]s it for each body, so moving bodies allocates nothing.
 */
internal class BodyBox {
    var transform: Transform = Transform()
        private set
    var offsetX: Float = 0f
        private set
    var offsetY: Float = 0f
        private set
    var halfWidth: Float = 0f
        private set
    var halfHeight: Float = 0f
        private set
    var mask: Int = 0
        private set
    var dropLine: Float = Float.NEGATIVE_INFINITY
        private set

    val centerX: Float get() = transform.x + offsetX
    val centerY: Float get() = transform.y + offsetY

    /** Describes the box of the body at [transform] and returns it. */
    fun set(
        transform: Transform,
        offsetX: Float,
        offsetY: Float,
        halfWidth: Float,
        halfHeight: Float,
        mask: Int,
        dropLine: Float,
    ): BodyBox {
        this.transform = transform
        this.offsetX = offsetX
        this.offsetY = offsetY
        this.halfWidth = halfWidth
        this.halfHeight = halfHeight
        this.mask = mask
        this.dropLine = dropLine
        return this
    }

    override fun toString(): String = "BodyBox(centerX=$centerX, centerY=$centerY, halfWidth=$halfWidth, halfHeight=$halfHeight)"
}

/**
 * Moves [box] by the shift of [carrier], the solid it stands on: up or down first, so it stays a
 * contact gap above the solid's top, then across, each stopping a contact gap short of any other
 * solid in the way. A body that stood on the top before the solid moved ends on it exactly, so
 * rounding cannot leave it inside the solid or floating above it. Returns `false` if the box ends
 * inside [carrier], because a solid above stopped it while its floor went on rising.
 */
private fun SolidGrid.carry(box: BodyBox, carrier: Int): Boolean {
    val gap = Collisions.CONTACT_GAP
    val shiftX = shiftXOf(carrier)
    val shiftY = shiftYOf(carrier)
    val centerY = box.centerY
    val feet = centerY + box.halfHeight
    val restingY = topOf(carrier) - gap - box.halfHeight
    // On the top before the move, within a gap: land on the new top exactly.
    val moveY = if (abs(feet - (topOf(carrier) - shiftY - gap)) <= gap) restingY - centerY else shiftY
    if (moveY != 0f) box.transform.y += sweepStop(box, carrier, 0f, moveY)
    if (shiftX != 0f) box.transform.x += sweepStop(box, carrier, shiftX, 0f)
    return box.centerY + box.halfHeight <= topOf(carrier) ||
        box.centerX + box.halfWidth <= leftOf(carrier) || box.centerX - box.halfWidth >= rightOf(carrier)
}

/**
 * Returns how far [box] gets along one axis of the move by ([moveX], [moveY]), one of which is 0,
 * before it comes a contact gap short of a solid other than [ignored], or the whole move if
 * nothing is in the way. The last sweep's [SolidGrid.hitSolid] says what stopped it.
 */
private fun SolidGrid.sweepStop(box: BodyBox, ignored: Int, moveX: Float, moveY: Float): Float {
    val gap = Collisions.CONTACT_GAP
    val centerX = box.centerX
    val centerY = box.centerY
    val halfWidth = box.halfWidth
    val halfHeight = box.halfHeight
    val isHit = sweep(
        centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight,
        moveX, moveY, box.mask, skipsOverlaps = true, dropLine = box.dropLine, ignores = ignored,
    )
    if (!isHit) return if (moveX != 0f) moveX else moveY
    val solid = hitSolid
    return when {
        moveX > 0f -> max(0f, min(moveX, leftOf(solid) - gap - halfWidth - centerX))
        moveX < 0f -> min(0f, max(moveX, rightOf(solid) + gap + halfWidth - centerX))
        moveY > 0f -> max(0f, min(moveY, topOf(solid) - gap - halfHeight - centerY))
        else -> min(0f, max(moveY, bottomOf(solid) + gap + halfHeight - centerY))
    }
}

/** What [pushByMovedSolid] returns when the solid did not touch the body. */
private const val NOT_PUSHED: Int = 0

/** What [pushByMovedSolid] returns when the solid pushed the body clear of it. */
private const val PUSHED: Int = 1

/** What [pushByMovedSolid] returns when another solid stopped the push before the body was clear. */
private const val CRUSHED: Int = 2

/**
 * Pushes [box] out of the way of [solid], which moved by its shift since the last move, if the
 * solid moved into it: if the solid passed through the box, as [hasMovedInto] says, and the box
 * lies ahead of the old box along the move, its side facing the move beyond the old box's. The
 * push goes along the solid's move, on the axis that clears the box soonest when the solid moved
 * on both, until the box is a contact gap past the solid's new box.
 * One-way solids and solids outside the box's mask push nothing, and a box behind the old box's
 * leading side, such as one already inside the solid, is left to [pushOut].
 *
 * Returns [NOT_PUSHED], [PUSHED] or [CRUSHED].
 */
private fun SolidGrid.pushByMovedSolid(box: BodyBox, solid: Int): Int {
    val canPush = !isOneWayAt(solid) && layersOf(solid) and box.mask != 0 && hasMovedInto(box, solid)
    val pushX = if (canPush) pushAcrossOf(box, solid) else Float.NaN
    val pushY = if (canPush) pushUpOrDownOf(box, solid) else Float.NaN
    if (pushX.isNaN() && pushY.isNaN()) return NOT_PUSHED
    val isAcross = pushY.isNaN() || (!pushX.isNaN() && abs(pushX) <= abs(pushY))
    val wanted = if (isAcross) pushX else pushY
    val moved = if (isAcross) sweepStop(box, solid, wanted, 0f) else sweepStop(box, solid, 0f, wanted)
    if (isAcross) box.transform.x += moved else box.transform.y += moved
    return if (moved == wanted) PUSHED else CRUSHED
}

/**
 * Returns whether [solid] passed through [box] while it moved by its shift: whether the box shares
 * area with the solid at some point of the move. A move along one axis covers the union of the old
 * and new boxes; a move on both is swept, its old center moved along the shift against the body's
 * box grown by the solid's half size, so a solid moving past a corner of the box does not count.
 */
private fun SolidGrid.hasMovedInto(box: BodyBox, solid: Int): Boolean {
    val shiftX = shiftXOf(solid)
    val shiftY = shiftYOf(solid)
    val left = box.centerX - box.halfWidth
    val right = box.centerX + box.halfWidth
    val top = box.centerY - box.halfHeight
    val bottom = box.centerY + box.halfHeight
    if (shiftX == 0f || shiftY == 0f) {
        val overlapsAcross = left < max(rightOf(solid), rightOf(solid) - shiftX) && min(leftOf(solid), leftOf(solid) - shiftX) < right
        val overlapsUpOrDown = top < max(bottomOf(solid), bottomOf(solid) - shiftY) && min(topOf(solid), topOf(solid) - shiftY) < bottom
        return overlapsAcross && overlapsUpOrDown
    }
    val halfWidth = (rightOf(solid) - leftOf(solid)) * 0.5f
    val halfHeight = (bottomOf(solid) - topOf(solid)) * 0.5f
    val startX = leftOf(solid) + halfWidth - shiftX
    val startY = topOf(solid) + halfHeight - shiftY
    // When the old center, moving along the shift, is inside the grown box on each axis.
    val leftTime = (left - halfWidth - startX) / shiftX
    val rightTime = (right + halfWidth - startX) / shiftX
    val topTime = (top - halfHeight - startY) / shiftY
    val bottomTime = (bottom + halfHeight - startY) / shiftY
    val enter = max(min(leftTime, rightTime), min(topTime, bottomTime))
    val exit = min(max(leftTime, rightTime), max(topTime, bottomTime))
    return max(enter, 0f) < min(exit, 1f)
}

/**
 * Returns how far across [box] must go along the move of [solid] to end a contact gap past its new
 * box, or NaN when the solid did not move across or the box's side facing the move is not beyond
 * the old box's.
 */
private fun SolidGrid.pushAcrossOf(box: BodyBox, solid: Int): Float {
    val shift = shiftXOf(solid)
    val left = box.centerX - box.halfWidth
    val right = box.centerX + box.halfWidth
    return when {
        shift > 0f && right > rightOf(solid) - shift -> rightOf(solid) + Collisions.CONTACT_GAP - left
        shift < 0f && left < leftOf(solid) - shift -> leftOf(solid) - Collisions.CONTACT_GAP - right
        else -> Float.NaN
    }
}

/** Returns how far up or down [box] must go along the move of [solid], as [pushAcrossOf] does across. */
private fun SolidGrid.pushUpOrDownOf(box: BodyBox, solid: Int): Float {
    val shift = shiftYOf(solid)
    val top = box.centerY - box.halfHeight
    val bottom = box.centerY + box.halfHeight
    return when {
        shift > 0f && bottom > bottomOf(solid) - shift -> bottomOf(solid) + Collisions.CONTACT_GAP - top
        shift < 0f && top < topOf(solid) - shift -> topOf(solid) - Collisions.CONTACT_GAP - bottom
        else -> Float.NaN
    }
}

/**
 * Returns the solid [body] stood on at the end of its last move, whether or not its `dy` has been
 * set upward since, if that solid moved since then, or -1.
 */
internal fun SolidGrid.movedFloorOf(body: KinematicBody): Int {
    val floor = if (body.isTouchingFloor) solidOf(body.floorRaw) else -1
    return if (floor >= 0 && (shiftXOf(floor) != 0f || shiftYOf(floor) != 0f)) floor else -1
}

/**
 * Carries [body], whose box is [box], by its floor if the floor moved since the last move and the
 * body still stands on it, then lets every other solid that moved into it push it, and sets
 * [KinematicBody.wasCarried] and, for a push that got the body clear, [KinematicBody.wasPushedOut].
 * Returns `true` if a solid in the way stopped the carry up or a push, which leaves the body
 * crushed; a carry up by a one-way solid that was stopped leaves the body inside it, not crushed,
 * to fall through it.
 */
internal fun SolidGrid.carryAndPush(body: KinematicBody, box: BodyBox): Boolean {
    // The floor of the last move, if it moved since: it carries a body still standing on it and
    // never pushes the body, so a body that jumped off it leaves by its own speed alone.
    val floor = movedFloorOf(body)
    // Carried first, so a body on a platform going down stays on it rather than dipping off it.
    val carrier = if (body.isOnFloor) floor else -1
    // A carry up that another solid stopped leaves the body inside its floor: crushed, unless the
    // floor is one-way, which the body then drops through in its own move.
    var result = if (carrier >= 0 && !carry(box, carrier) && !isOneWayAt(carrier)) CRUSHED else NOT_PUSHED
    body.wasCarried = carrier >= 0
    // Only the solids whose move covered a cell the body touches can have moved into it.
    val nearby = shiftedNear(
        box.centerX - box.halfWidth, box.centerY - box.halfHeight,
        box.centerX + box.halfWidth, box.centerY + box.halfHeight,
    )
    for (k in 0 until nearby) {
        val solid = nearAt(k)
        if (solid != floor) result = max(result, pushByMovedSolid(box, solid))
    }
    body.wasPushedOut = result == PUSHED
    return result == CRUSHED
}
