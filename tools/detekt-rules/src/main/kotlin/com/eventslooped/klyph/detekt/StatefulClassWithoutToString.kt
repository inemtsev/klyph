package com.eventslooped.klyph.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * API_STYLE.md section 4: every public type that holds state overrides `toString` in a readable
 * format.
 *
 * The rule checks the part of that a parser can see: a public class that declares a `var`, in its
 * primary constructor or in its body, with any visibility, must declare `override fun toString()`
 * itself. A `var` without a backing field (a getter with a body, no initializer and no `field`)
 * holds no state of its own and does not count, nor does an abstract one. Interfaces, enums,
 * annotation and data classes are left out; data classes have their own rule.
 *
 * <noncompliant>
 * public class Counter {
 *     public var count: Int = 0
 * }
 * </noncompliant>
 *
 * <compliant>
 * public class Counter {
 *     public var count: Int = 0
 *
 *     override fun toString(): String = "Counter(count=$count)"
 * }
 * </compliant>
 */
class StatefulClassWithoutToString(config: Config) : Rule(
    config,
    "A public class that holds var state overrides toString.",
) {
    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (klass is KtEnumEntry || klass.isInterface() || klass.isEnum() || klass.isAnnotation() || klass.isData()) return
        if (!klass.isPublicApi() || !klass.holdsVarState() || klass.declaresToString()) return
        report(
            Finding(
                Entity.atName(klass),
                "Public class ${klass.name} holds var state but does not override toString.",
            ),
        )
    }

    private fun KtClass.holdsVarState(): Boolean =
        primaryConstructorParameters.any { it.isMutable } || getProperties().any { it.holdsState() }

    private fun KtProperty.holdsState(): Boolean {
        if (!isVar || hasModifier(KtTokens.ABSTRACT_KEYWORD)) return false
        if (hasInitializer() || hasDelegate() || getter?.hasBody() != true) return true
        return accessors.any { accessor ->
            accessor.collectDescendantsOfType<KtNameReferenceExpression>().any { it.getReferencedName() == "field" }
        }
    }

    private fun KtClass.declaresToString(): Boolean = declarations.any {
        it is KtNamedFunction &&
            it.name == "toString" &&
            it.valueParameters.isEmpty() &&
            it.hasModifier(KtTokens.OVERRIDE_KEYWORD)
    }
}
