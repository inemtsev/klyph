package samples.collision

import klyph.collision.CollisionLayers
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollisionLayerSets {
    @Sample
    fun collisionLayers() {
        val player = CollisionLayers.of(0)
        val enemy = CollisionLayers.of(1)
        val bullet = CollisionLayers.of(2)

        val enemyMask = player + bullet
        assertPrints(enemyMask, "CollisionLayers(0, 2)")
        assertTrue(enemyMask.contains(bullet))
        assertFalse(enemyMask.contains(enemy))
        assertTrue(CollisionLayers.ALL.contains(enemy))
        assertPrints(CollisionLayers.NONE, "CollisionLayers()")
        assertFailsWith<IllegalArgumentException> { CollisionLayers.of(32) }
    }
}
