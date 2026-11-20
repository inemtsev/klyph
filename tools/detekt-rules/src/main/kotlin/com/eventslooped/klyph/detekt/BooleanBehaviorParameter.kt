package com.eventslooped.klyph.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * API_STYLE.md section 3: a parameter of a public function or constructor is never a bare
 * `Boolean` that selects behavior; split the function or take an enum instead.
 *
 * Without type resolution the rule cannot tell a flag from a value, so it reports every `Boolean`
 * parameter, nullable or not, of a public function or constructor. Two kinds are left alone: a
 * `val` or `var` in a primary constructor, which declares a property holding state rather than
 * choosing what the call does, and the parameters of an override, which belong to the
 * declaration it overrides.
 *
 * <noncompliant>
 * public fun Image.flip(horizontal: Boolean): Image
 * </noncompliant>
 *
 * <compliant>
 * public fun Image.flippedHorizontally(): Image
 * public fun Image.flippedVertically(): Image
 * </compliant>
 */
class BooleanBehaviorParameter(config: Config) : Rule(
    config,
    "A public function or constructor takes no Boolean parameter that selects behavior; split it or take an enum.",
) {
    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD) || !function.isPublicApi()) return
        function.valueParameters.forEach { check(it, "function ${function.name}") }
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        super.visitPrimaryConstructor(constructor)
        checkConstructor(constructor)
    }

    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
        super.visitSecondaryConstructor(constructor)
        checkConstructor(constructor)
    }

    private fun checkConstructor(constructor: KtConstructor<*>) {
        val owner: KtClassOrObject = constructor.containingClassOrObject ?: return
        if (!constructor.isPublicApi()) return
        constructor.valueParameters
            .filterNot { it.hasValOrVar() }
            .forEach { check(it, "constructor of ${owner.name}") }
    }

    private fun check(parameter: KtParameter, owner: String) {
        if (parameter.typeReference?.isBoolean() == true) {
            report(
                Finding(
                    Entity.from(parameter),
                    "Parameter ${parameter.name} of public $owner is a Boolean; split the function or take an enum.",
                ),
            )
        }
    }
}
