package samples.app

import klyph.app.Resources
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Score(var points: Int) {
    override fun toString(): String = "Score($points)"
}

class GameResources {
    @Sample
    fun insert() {
        val resources = Resources()
        resources.insert(Score(0))
        resources.get<Score>().points += 5
        assertPrints(resources.get<Score>(), "Score(5)")

        resources.insert(Score(100))
        assertPrints(resources.get<Score>(), "Score(100)")
    }

    @Sample
    fun getOrNull() {
        val resources = Resources()
        assertPrints(resources.getOrNull<Score>(), "null")
        assertFalse(resources.has<Score>())

        resources.insert(Score(3))
        assertTrue(resources.has<Score>())
        assertPrints(resources.remove<Score>(), "Score(3)")
        assertPrints(resources.getOrNull<Score>(), "null")
    }
}
