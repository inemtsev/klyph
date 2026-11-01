package samples.entity

import klyph.entity.Commands
import klyph.entity.ComponentType
import klyph.entity.World
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandQueues {
    @Sample
    fun spawn() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        val commands = Commands(world)
        val player = commands.spawn {
            add(Health, 10)
        }
        val sword = commands.spawn {
            parent(player)
        }
        assertTrue(world.isAlive(player))
        assertPrints(world.getOrNull(player, Health), "null")

        commands.apply()
        assertPrints(world.get(player, Health), "10")
        assertTrue(world.parentOrNull(sword) == player)
    }

    @Sample
    fun destroy() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        val commands = Commands(world)
        val enemy = world.create()
        world.add(enemy, Health, 0)

        world.forEach(Health) { entity, health ->
            if (health <= 0) commands.destroy(entity)
        }
        assertPrints(commands, "Commands(queued=1)")

        commands.apply()
        assertFalse(world.isAlive(enemy))
    }
}
