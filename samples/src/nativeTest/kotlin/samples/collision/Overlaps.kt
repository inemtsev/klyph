package samples.collision

import klyph.collision.Overlap
import klyph.collision.contains
import klyph.collision.otherOrNull
import klyph.collision.otherThan
import klyph.entity.World
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith

class Overlaps {
    @Sample
    fun otherThan() {
        val world = World()
        val enemy = world.create()
        val bullet = world.create()
        val overlap = Overlap(enemy, bullet)
        assertPrints(bullet in overlap, "true")
        assertPrints(overlap.otherThan(bullet) == enemy, "true")
        assertFailsWith<IllegalArgumentException> { overlap.otherThan(world.create()) }
    }

    @Sample
    fun otherOrNull() {
        val world = World()
        val player = world.create()
        val coin = world.create()
        val overlap = Overlap(coin, player)
        assertPrints(overlap.otherOrNull(player) == coin, "true")
        // A handler for the player's overlaps skips every pair the player is not in.
        assertPrints(overlap.otherOrNull(world.create()), "null")
    }
}
