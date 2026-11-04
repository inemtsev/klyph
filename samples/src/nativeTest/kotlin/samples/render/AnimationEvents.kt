package samples.render

import klyph.entity.World
import klyph.event.Events
import klyph.render.AnimationFinished
import samples.Sample
import samples.assertPrints

class AnimationEvents {
    @Sample
    fun animationFinished() {
        val world = World()
        val explosion = world.create()
        val events = Events()
        // The engine publishes this when the explosion's ONCE animation has played.
        events.publish(AnimationFinished(explosion))
        var finished = 0
        events.forEach<AnimationFinished> { if (it.entity == explosion) finished++ }
        assertPrints(finished, "1")
    }
}
