package com.eventslooped.klyph.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * API_STYLE.md section 7: the first sentence of a public function's KDoc is third-person
 * indicative and starts with a verb, such as "Returns", "Creates", "Adds" or "Throws".
 *
 * The check is syntactic: with no type resolution and no dictionary, the first word passes when it
 * is a capitalized word ending in `s` outside a deny list, such as "Returns" or "Zero-fills", once
 * any backticks or asterisks around it are stripped. The deny list turns away common words that
 * end in `s` without being third-person verbs, such as "This", "Bounds", "Status" and "Process".
 * Anything else that fits the pattern passes, so the rule misses a noun or an imperative ending
 * in `s` that the list does not name ("Pixels of the image", "Pass the value"), and it cannot
 * tell whether the sentence goes on to say what the function does.
 * A function without KDoc, or whose KDoc has only tags, is left to UndocumentedPublicFunction.
 * Types and properties may open with a noun phrase, so only functions are checked; an override
 * with KDoc is checked like any function.
 *
 * A KDoc that opens "The first node reachable from [start]." is reported; "Returns the first node
 * reachable from [start]." is not.
 */
class FunctionKDocStartsWithVerb(config: Config) : Rule(
    config,
    "The KDoc of a public function opens with a third-person verb, such as Returns, Creates, Adds or Throws.",
) {
    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.isPublicApi()) return
        val content = function.docComment?.getDefaultSection()?.getContent()?.trim().orEmpty()
        if (content.isEmpty()) return
        val firstWord = content.takeWhile { !it.isWhitespace() }
        if (!firstWord.trimStart('`', '*').trimEnd('`', '*', ',', ':', ';', '.').isThirdPersonVerb()) {
            report(
                Finding(
                    Entity.atName(function),
                    "The KDoc of public function ${function.name} opens with '$firstWord' instead of a " +
                        "third-person verb such as Returns, Creates, Adds or Throws.",
                ),
            )
        }
    }

    private fun String.isThirdPersonVerb(): Boolean =
        length > 2 && first().isUpperCase() && drop(1).all { it.isLowerCase() || it == '-' } && endsWith('s') && this !in NOT_VERBS

    private companion object {
        // Words ending in s that open a sentence without being a third-person verb: nouns,
        // imperatives and other words. "Is" and "Was" are verbs, but a function's KDoc opens with
        // what it does: "Returns `true` if ...".
        val NOT_VERBS = setOf(
            "Access", "Across", "Address", "Alias", "Always", "Analysis", "As", "Basis", "Bonus", "Bounds",
            "Canvas", "Class", "Focus", "Is", "Its", "Minus", "Perhaps", "Plus", "Process", "Progress",
            "Radius", "Series", "Status", "This", "Thus", "Unless", "Versus", "Was", "Whereas", "Yes",
        )
    }
}
