package klyph.entity

/** Links a child entity to its parent. Maintained only by [World.setParent] and [World.removeParent]. */
internal class Parent(var entity: Entity) {
    override fun toString(): String = "Parent($entity)"

    companion object : ComponentType<Parent>("Parent")
}

/** Children of an entity in attach order. Removed from the entity when the last child leaves. */
internal class Children {
    val entities: MutableList<Entity> = ArrayList(4)

    override fun toString(): String = "Children($entities)"

    companion object : ComponentType<Children>("Children")
}
