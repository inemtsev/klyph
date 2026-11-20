package com.eventslooped.klyph.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * The `api-style` rule set: the rules of klyph's API_STYLE.md that stock detekt cannot express.
 *
 * Every rule works on syntax alone, because detekt has no type resolution for Kotlin/Native.
 * detekt finds this class through `META-INF/services/dev.detekt.api.RuleSetProvider`.
 */
class ApiStyleRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("api-style")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ::FunctionKDocStartsWithVerb,
            ::BooleanBehaviorParameter,
            ::StatefulClassWithoutToString,
        ),
    )
}
