package klyph.entity

/**
 * Position, rotation and scale of an entity in world space, after its [Transform] is combined
 * with the world transforms of its ancestors. Rotation is in radians, counter-clockwise.
 *
 * [propagateTransforms] computes it every frame and adds it to each entity that has a
 * [Transform]. Never set it yourself: the next propagation overwrites it, and the constructor
 * is not public for that reason. Read it to find where an entity ends up in the world.
 *
 * @sample samples.entity.Hierarchies.propagateTransforms
 */
public class WorldTransform internal constructor(
    /** Horizontal position in world units. */
    public var x: Float = 0f,
    /** Vertical position in world units. */
    public var y: Float = 0f,
    /** Rotation in radians, counter-clockwise. */
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
