package com.eventslooped.klyph.detekt

import dev.detekt.api.RuleName
import dev.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiStyleRuleSetProviderTest {
    @Test
    fun `is found through the service loader with its three rules`() {
        val provider = ServiceLoader.load(RuleSetProvider::class.java).single { it is ApiStyleRuleSetProvider }
        val ruleSet = provider.instance()
        assertEquals("api-style", ruleSet.id.value)
        assertEquals(
            setOf("FunctionKDocStartsWithVerb", "BooleanBehaviorParameter", "StatefulClassWithoutToString").map(::RuleName).toSet(),
            ruleSet.rules.keys,
        )
    }
}
