package samples.event

import klyph.event.Events
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreChanged(val points: Int) {
    override fun toString(): String = "ScoreChanged($points)"
}

class EventBus {
    @Sample
    fun publish() {
        val events = Events()
        events.publish(ScoreChanged(10))
        events.publish(ScoreChanged(25))

        var total = 0
        events.forEach<ScoreChanged> { total += it.points }
        assertPrints(total, "35")
    }

    @Sample
    fun any() {
        val events = Events()
        assertFalse(events.any<ScoreChanged>())
        events.publish(ScoreChanged(10))
        assertTrue(events.any<ScoreChanged>())
    }
}
