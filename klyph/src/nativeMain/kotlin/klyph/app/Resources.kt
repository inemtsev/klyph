package klyph.app

import kotlin.reflect.KClass

/**
 * Holds at most one value per class, such as a score, a settings object or a level, so systems
 * can share state that belongs to no entity.
 *
 * Resources are matched by their exact runtime class: a resource inserted as an instance of a
 * subclass is found by that subclass, not by one of its supertypes or interfaces.
 *
 * The constructor is public so resources can be used without an app, for example in tests.
 * [toString] gives the number of resources, for example `Resources(size=2)`.
 *
 * Not thread-safe.
 */
public class Resources {
    @PublishedApi
    internal val values: HashMap<KClass<*>, Any> = HashMap()

    /** Number of resources held. */
    public val size: Int get() = values.size

    /**
     * Stores [resource] under its runtime class, replacing the resource of that class if there is
     * one.
     *
     * @sample samples.app.GameResources.insert
     */
    public fun <T : Any> insert(resource: T) {
        values[resource::class] = resource
    }

    /**
     * Returns the resource of class [T].
     *
     * @throws NoSuchElementException if no resource of exactly class [T] is held.
     *
     * @sample samples.app.GameResources.insert
     */
    public inline fun <reified T : Any> get(): T =
        getOrNull<T>() ?: throw NoSuchElementException("No resource of type ${T::class.simpleName} is registered.")

    /**
     * Returns the resource of class [T], or `null` if none is held.
     *
     * @sample samples.app.GameResources.getOrNull
     */
    public inline fun <reified T : Any> getOrNull(): T? = values[T::class] as T?

    /**
     * Removes the resource of class [T] and returns it, or returns `null` if none was held.
     *
     * @sample samples.app.GameResources.getOrNull
     */
    public inline fun <reified T : Any> remove(): T? = values.remove(T::class) as T?

    /**
     * Returns `true` if a resource of class [T] is held.
     *
     * @sample samples.app.GameResources.getOrNull
     */
    public inline fun <reified T : Any> has(): Boolean = values.containsKey(T::class)

    override fun toString(): String = "Resources(size=$size)"
}
