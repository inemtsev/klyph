package com.eventslooped.klyph.detekt

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Returns whether this declaration is part of the public API as API_STYLE.md scopes it: public,
 * or `internal` with `@PublishedApi`, and declared at the top of a file or in classes and objects
 * that are public the same way, never inside a function body or an object expression.
 *
 * Protected members count as not public, as detekt's own UndocumentedPublicFunction counts them.
 */
internal fun KtDeclaration.isPublicApi(): Boolean {
    if (!isVisibleOutsideItsModule()) return false
    var element: PsiElement? = parent
    while (element !is KtFile) {
        when (element) {
            is KtClassOrObject -> if (!element.isVisibleOutsideItsModule()) return false
            is KtClassBody -> Unit
            else -> return false
        }
        element = element.parent
    }
    return true
}

private fun KtDeclaration.isVisibleOutsideItsModule(): Boolean = when {
    hasModifier(KtTokens.PRIVATE_KEYWORD) || hasModifier(KtTokens.PROTECTED_KEYWORD) -> false
    hasModifier(KtTokens.INTERNAL_KEYWORD) -> annotationEntries.any { it.shortName?.asString() == "PublishedApi" }
    this is KtConstructor<*> -> hasPublicDefaultVisibility()
    else -> true
}

/**
 * Returns whether a constructor without a visibility modifier is public: a sealed class's
 * constructor is protected by default and an enum class's private, every other one public. The
 * class's own visibility is checked by [isPublicApi] as it walks out to the file.
 */
private fun KtConstructor<*>.hasPublicDefaultVisibility(): Boolean {
    val owner = containingClassOrObject ?: return true
    return !owner.hasModifier(KtTokens.SEALED_KEYWORD) && !(owner is KtClass && owner.isEnum())
}

/** Returns whether this type reference names `Boolean`, nullable or not, with or without its package. */
internal fun KtTypeReference.isBoolean(): Boolean =
    text.filterNot { it.isWhitespace() }.removeSuffix("?") in BOOLEAN_NAMES

private val BOOLEAN_NAMES = setOf("Boolean", "kotlin.Boolean")
