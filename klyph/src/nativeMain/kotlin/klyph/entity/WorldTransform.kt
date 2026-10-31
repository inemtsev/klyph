package klyph.entity

/**
 * Position, rotation and scale of an entity in world space, after its [Transform] is combined
 * with the world transforms of its ancestors. Rotation is in radians; a positive rotation turns
 * clockwise on screen, because y points down.
 *
 * Only entities that have a parent, at any depth of a hierarchy, have one. [propagateTransforms]
 * computes it every frame and adds it to each such entity that has a [Transform];
 * [World.removeParent] removes it. An entity without a parent has none, because its [Transform]
 * already is in world space. To find where any entity ends up in the world, read its
 * [WorldTransform] if it has one and its [Transform] otherwise.
 *
 * Never set it yourself: the next propagation overwrites it, and the constructor is not public
 * for that reason.
 *
 * @sample samples.entity.Hierarchies.propagateTransforms
 */
public class WorldTransform internal constructor(
    /** Horizontal position in world units. */
    public var x: Float = 0f,
    /** Vertical position in world units. */
    public var y: Float = 0f,
    /** Rotation in radians; positive turns clockwise on screen, because y points down. */
    public var rotation: Float = 0f,
    /** Horizontal scale factor. */
    public var scaleX: Float = 1f,
    /** Vertical scale factor. */
    public var scaleY: Float = 1f,
) {
    override fun toString(): String = "WorldTransform(x=$x, y=$y, rotation=$rotation, scale=$scaleX x $scaleY)"

    /** Component key for [WorldTransform]. */
    public companion object : ComponentType<WorldTransform>("WorldTransform")
}
