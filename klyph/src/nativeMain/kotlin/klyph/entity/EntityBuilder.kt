package klyph.entity

/**
 * Receiver of the block passed to [Commands.spawn]. Queues components and a parent for the entity
 * being spawned; they attach when the [Commands] are applied.
 *
 * Use it only inside that block. Each [Commands] reuses one builder for every spawn, so a builder
 * kept after its block returns refers to whichever entity is being spawned at the time.
 *
 * [toString] gives the entity being spawned, for example `EntityBuilder(entity=Entity(3:0))`.
 */
public class EntityBuilder internal constructor(private val commands: Commands) {
    internal var current: Entity = Entity(-1L)

    /** The entity being spawned, so commands queued inside the block can refer to it. */
    public val entity: Entity get() = current

    /**
     * Queues attaching [component] to the spawned entity under [type], replacing any component of
     * that type queued before it.
     *
     * @sample samples.entity.CommandQueues.spawn
     */
    public fun <T : Any> add(type: ComponentType<T>, component: T) {
        commands.add(current, type, component)
    }

    /**
     * Queues making [parent] the parent of the spawned entity, as [Commands.setParent] does.
     * Skipped at apply if [parent] is no longer alive then.
     *
     * @throws IllegalArgumentException if [parent] is the spawned entity itself.
     *
     * @sample samples.entity.CommandQueues.spawn
     */
    public fun parent(parent: Entity) {
        commands.setParent(current, parent)
    }

    override fun toString(): String = "EntityBuilder(entity=$current)"
}
