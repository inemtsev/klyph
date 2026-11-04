package klyph.entity

/**
 * Queues changes to a [World] so they happen together at [apply] instead of right away.
 *
 * A system that iterates a query must not add or remove components of the queried types inside
 * the loop. It queues the change here instead, and the change takes effect when [apply] runs,
 * after the loop. Commands run in the order they were queued.
 *
 * A command whose entity is no longer alive when [apply] runs is skipped without an error,
 * because destroying an entity that other commands still refer to is a normal event within a
 * frame. A [setParent] whose child or parent is no longer alive is skipped the same way.
 *
 * In an app, systems use `App.commands`, which the engine applies after every system, so the next
 * system sees the changes, and once more after the transform propagation that follows
 * `Phase.LATE_UPDATE`. A queue of one's own is applied by calling [apply].
 *
 * The constructor is public so the queue can be used without an app, for example in tests and
 * tools. [toString] gives the number of queued commands, for example `Commands(queued=3)`.
 *
 * Not thread-safe.
 */
public class Commands(private val world: World) {
    private var kinds = IntArray(INITIAL_CAPACITY)
    private var entities = LongArray(INITIAL_CAPACITY)
    private var parents = LongArray(INITIAL_CAPACITY)
    private var types = arrayOfNulls<ComponentType<*>>(INITIAL_CAPACITY)
    private var components = arrayOfNulls<Any>(INITIAL_CAPACITY)
    private var isApplying = false
    private val entityBuilder = EntityBuilder(this)

    /** Number of commands queued since the last [apply]. */
    public var size: Int = 0
        private set

    /** Returns `true` if no command is queued. */
    public fun isEmpty(): Boolean = size == 0

    /**
     * Creates an entity right away and queues the components and parent that [builder] gives it.
     *
     * The returned handle is valid at once, so it can be passed to further commands or stored in
     * game state. Until [apply] runs, the entity is alive but has no components, so queries do not
     * see it. Inside [builder], [EntityBuilder.entity] is the new entity, so commands queued there
     * can refer to it.
     *
     * @sample samples.entity.CommandQueues.spawn
     */
    public fun spawn(builder: EntityBuilder.() -> Unit = {}): Entity {
        val entity = world.create()
        val previous = entityBuilder.current
        entityBuilder.current = entity
        try {
            entityBuilder.builder()
        } finally {
            entityBuilder.current = previous
        }
        return entity
    }

    /**
     * Queues removing [entity], all its descendants and all their components, as [World.destroy]
     * does. Does nothing at [apply] if [entity] is already gone.
     *
     * @sample samples.entity.CommandQueues.destroy
     */
    public fun destroy(entity: Entity) {
        push(DESTROY, entity.raw, 0L, null, null)
    }

    /**
     * Queues attaching [component] to [entity] under [type], replacing any component of that type,
     * as [World.add] does. Skipped if [entity] is not alive at [apply].
     *
     * @sample samples.entity.CommandQueues.spawn
     */
    public fun <T : Any> add(entity: Entity, type: ComponentType<T>, component: T) {
        push(ADD, entity.raw, 0L, type, component)
    }

    /**
     * Queues detaching the [type] component from [entity], as [World.remove] does. Skipped if
     * [entity] is not alive at [apply] or has no such component then.
     *
     * @sample samples.entity.CommandQueues.remove
     */
    public fun remove(entity: Entity, type: ComponentType<*>) {
        push(REMOVE, entity.raw, 0L, type, null)
    }

    /**
     * Queues making [parent] the parent of [child], as [World.setParent] does. Skipped if either
     * entity is not alive at [apply].
     *
     * @throws IllegalArgumentException if [child] equals [parent].
     *
     * @sample samples.entity.CommandQueues.spawn
     */
    public fun setParent(child: Entity, parent: Entity) {
        require(child != parent) { "Entity $child cannot be its own parent." }
        push(SET_PARENT, child.raw, parent.raw, null, null)
    }

    /**
     * Runs every queued command against the world in the order it was queued, then empties the
     * queue.
     *
     * A queued [setParent] whose parent has become a descendant of its child would create a cycle,
     * so it throws. The commands queued after it are dropped and the queue is left empty.
     *
     * @throws IllegalArgumentException if a queued [setParent] would create a cycle in the
     *   hierarchy.
     * @throws IllegalStateException if this queue is already being applied.
     *
     * @sample samples.entity.CommandQueues.spawn
     */
    public fun apply() {
        check(!isApplying) { "Commands are already being applied." }
        isApplying = true
        try {
            for (i in 0 until size) run(i)
        } finally {
            clear()
            isApplying = false
        }
    }

    /**
     * Empties the queue without running its commands. The engine calls it when a scene action
     * throws, so the commands it queued do not apply later as if they belonged to no scene.
     */
    internal fun discard() {
        clear()
    }

    override fun toString(): String = "Commands(queued=$size)"

    @Suppress("UNCHECKED_CAST")
    private fun run(i: Int) {
        val entity = Entity(entities[i])
        when (kinds[i]) {
            DESTROY -> world.destroy(entity)
            ADD -> if (world.isAlive(entity)) {
                world.add(entity, types[i] as ComponentType<Any>, components[i] as Any)
            }
            REMOVE -> if (world.isAlive(entity)) world.remove(entity, types[i] as ComponentType<Any>)
            SET_PARENT -> {
                val parent = Entity(parents[i])
                if (world.isAlive(entity) && world.isAlive(parent)) world.setParent(entity, parent)
            }
        }
    }

    private fun push(kind: Int, entity: Long, parent: Long, type: ComponentType<*>?, component: Any?) {
        if (size == kinds.size) grow()
        kinds[size] = kind
        entities[size] = entity
        parents[size] = parent
        types[size] = type
        components[size] = component
        size++
    }

    private fun grow() {
        val capacity = kinds.size * 2
        kinds = kinds.copyOf(capacity)
        entities = entities.copyOf(capacity)
        parents = parents.copyOf(capacity)
        types = types.copyOf(capacity)
        components = components.copyOf(capacity)
    }

    /** Empties the queue and drops its references to queued components so they can be collected. */
    private fun clear() {
        types.fill(null, 0, size)
        components.fill(null, 0, size)
        size = 0
    }

    private companion object {
        const val INITIAL_CAPACITY = 64
        const val DESTROY = 0
        const val ADD = 1
        const val REMOVE = 2
        const val SET_PARENT = 3
    }
}
