package klyph.render

import klyph.entity.Entity
import klyph.entity.World
import klyph.platform.SamplerAddress

/**
 * Writes into [uv] the texture coordinates of this sprite, which has a tile size, in the order of
 * its record: u0, v0, u1 and v1. A flipped axis swaps its pair.
 *
 * An axis with a tile size runs from the region's start up to the number of repeats the quad shows,
 * `width / tileWidth` across and `height / tileHeight` down, which a [SamplerAddress.REPEAT]
 * sampler wraps; an axis whose tile size is 0 keeps the region's range. Both sizes are the
 * sprite's own, before the entity's scale, so the scale takes no part here. [world] holds the
 * sprite, so that a failed check can name its entity.
 *
 * The renderer calls this only for a sprite with a tile size. Every other sprite takes a shorter
 * path in the renderer, which this function is not part of.
 *
 * @throws IllegalStateException if [checkTiling] rejects the sprite.
 */
internal fun Sprite.tiledUv(world: World, uv: FloatArray) {
    checkTiling(world)
    val region = region
    val u0 = region.u0
    val v0 = region.v0
    var u1 = region.u1
    var v1 = region.v1
    if (tileWidth != 0f) u1 = u0 + width / tileWidth
    if (tileHeight != 0f) v1 = v0 + height / tileHeight
    if (isFlippedX) {
        uv[0] = u1
        uv[2] = u0
    } else {
        uv[0] = u0
        uv[2] = u1
    }
    if (isFlippedY) {
        uv[1] = v1
        uv[3] = v0
    } else {
        uv[1] = v0
        uv[3] = v1
    }
}

/**
 * Checks that this sprite, which has a tile size, can be drawn tiled: its texture repeats, its
 * region is the whole texture, and neither tile size is negative or not a number. The message
 * names the entity of [world] that has this sprite.
 *
 * Takes the world instead of the entity so that drawing carries no entity through its loop; the
 * lookup runs only when a check fails.
 *
 * @throws IllegalStateException if one of those does not hold.
 */
internal fun Sprite.checkTiling(world: World) {
    val texture = region.texture
    check(texture.address == SamplerAddress.REPEAT) {
        "${ownerIn(world)} has a Sprite with a tile size, which needs a texture created with SamplerAddress.REPEAT, " +
            "but $texture uses ${texture.address}."
    }
    check(region.isWholeTexture) {
        "${ownerIn(world)} has a Sprite with a tile size, which needs a region that covers its whole texture, " +
            "but its region is $region."
    }
    check(tileWidth >= 0f && tileHeight >= 0f && tileWidth.isFinite() && tileHeight.isFinite()) {
        "${ownerIn(world)} has a Sprite with tile size ${tileWidth}x$tileHeight, which is negative, infinite or not a number."
    }
}

/**
 * Checks that neither [Sprite.originX] nor [Sprite.originY] of this sprite is infinite or not a
 * number. A value outside 0 to 1 passes: an origin outside the quad is meaningful. The message
 * names the entity of [world] that has this sprite, as [checkTiling]'s does.
 *
 * [writeRecord] calls this only once its own test, a few instructions long, has found a bad
 * origin, so that every other sprite pays for no call.
 *
 * @throws IllegalStateException if one of the two is.
 */
internal fun Sprite.checkOrigin(world: World) {
    check(originX.isFinite() && originY.isFinite()) {
        "${ownerIn(world)} has a Sprite with origin ${originX}x$originY, which is infinite or not a number."
    }
}

/**
 * Returns the name of the first entity of [world] whose [Sprite] is this very instance, such as
 * `Entity(3:0)`, or `Unknown entity` if none has it. Walks every sprite, so call it only to build
 * a message.
 */
private fun Sprite.ownerIn(world: World): String {
    var owner: Entity? = null
    world.forEach(Sprite) { entity, sprite -> if (owner == null && sprite === this) owner = entity }
    return owner?.toString() ?: "Unknown entity"
}
