package com.eventslooped.klyph.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionKDocStartsWithVerbTest {
    private val rule = FunctionKDocStartsWithVerb(Config.empty)

    @Test
    fun `reports a public function whose KDoc opens with a noun phrase`() {
        val findings = rule.lint(
            """
            /** The first element of the list. */
            fun first(): Int = 0
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
        assertTrue("'The'" in findings.single().message)
    }

    @Test
    fun `reports an imperative verb, a lowercase verb and a word that only ends in s`() {
        val findings = rule.lint(
            """
            /** Return the first element. */
            fun first(): Int = 0

            /** returns the last element. */
            fun last(): Int = 0

            /** This adds one. */
            fun inc(): Int = 0

            /** [first] plus one. */
            fun second(): Int = 0
            """.trimIndent(),
        )
        assertEquals(4, findings.size)
    }

    @Test
    fun `reports nouns and imperatives on the deny list`() {
        val findings = rule.lint(
            """
            /** Bounds of the view. */
            fun bounds(): Int = 0

            /** Process the queued commands. */
            fun process() {}
            """.trimIndent(),
        )
        val openers = findings.map { it.message.substringAfter("with ").substringBefore(" instead") }
        assertEquals(listOf("'Bounds'", "'Process'"), openers)
    }

    @Test
    fun `accepts a verb wrapped in markdown`() {
        val findings = rule.lint(
            """
            /** `Returns` the first element. */
            fun first(): Int = 0

            /** **Adds** one. */
            fun inc(): Int = 0
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `accepts third-person verbs`() {
        val findings = rule.lint(
            """
            /** Returns the first element. */
            fun first(): Int = 0

            class Graph {
                /**
                 * Adds [node] to the graph.
                 *
                 * @throws IllegalArgumentException if it is already there.
                 */
                fun add(node: Int) {}

                /** Zero-fills the adjacency table. */
                fun clear() {}

                /** Returns "Graph". */
                override fun toString(): String = "Graph"
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `ignores functions outside the public API and functions without a description`() {
        val findings = rule.lint(
            """
            /** The first element. */
            private fun first(): Int = 0

            /** The last element. */
            internal fun last(): Int = 0

            internal class Hidden {
                /** The size. */
                fun size(): Int = 0
            }

            /** @return the count. */
            fun count(): Int = 0

            fun outer() {
                /** A local function. */
                fun local() {}
            }

            /** Some value. */
            val value: Int = 0
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `checks a published internal function`() {
        val findings = rule.lint(
            """
            /** The first element. */
            @PublishedApi
            internal fun first(): Int = 0
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
    }
}
