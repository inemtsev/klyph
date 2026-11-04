package klyph.collision

import klyph.entity.ComponentType

/**
 * Makes an entity take part in overlap detection: [Collisions] reports an [Overlap] for every
 * pair of colliders whose shapes overlap and whose layers match, and finds it in shape queries.
 *
 * The shape is centered on the entity's position plus [offsetX] and [offsetY]. The position is
 * the entity's `WorldTransform` when it has one, so a collider on a child follows its parent, and
 * its `Transform` otherwise; an entity with neither is ignored. The transform's scale multiplies
 * the offset and the shape: a box's width by the x scale and its height by the y scale, a circle's
 * radius by the x scale alone, so a circle stays a circle. A negative scale mirrors the offset and
 * leaves the size positive. The transform's rotation is ignored: boxes stay axis-aligned and the
 * offset does not turn.
 *
 * [layers] says which layers the collider is on and [mask] which layers it hits. Two colliders
 * overlap in [Collisions] terms when either one's mask contains a layer of the other, so the pair
 * is reported even when only one side asked for it.
 *
 * [toString] gives
 * `Collider(shape=Circle(radius=8.0), offset=0.0x0.0, layers=CollisionLayers(0), mask=CollisionLayers(1))`.
 *
 * @sample samples.collision.Colliders.collider
 */
public class Collider(
    /** The outline, centered on the entity's position plus the offset. */
    public var shape: Shape,
    /** The horizontal distance from the entity's position to the shape's center, before scale. */
    public var offsetX: Float = 0f,
    /** The vertical distance from the entity's position to the shape's center, before scale. */
    public var offsetY: Float = 0f,
    /** The layers this collider is on: what it is. */
    public var layers: CollisionLayers = CollisionLayers.DEFAULT,
    /** The layers this collider hits: what it looks for. */
    public var mask: CollisionLayers = CollisionLayers.ALL,
) {
    override fun toString(): String =
        "Collider(shape=$shape, offset=${offsetX}x$offsetY, layers=$layers, mask=$mask)"

    /** Component key for [Collider]. */
    public companion object : ComponentType<Collider>("Collider")
}
