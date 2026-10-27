package klyph.entity

/**
 * Key under which components of type [T] are stored in a [World]. Declare one per component
 * class, usually as its companion object, so the class name doubles as the key:
 *
 * ```
 * class Health(var points: Int) {
 *     companion object : ComponentType<Health>("Health")
 * }
 * world.add(entity, Health, Health(10))
 * ```
 *
 * Identity is by instance, not by [name]; the name is for messages and tooling.
 *
 * @sample samples.entity.Components.componentType
 */
public abstract class ComponentType<T : Any>(public val name: String) {
    override fun toString(): String = "ComponentType($name)"
}
