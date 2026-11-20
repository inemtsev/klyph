package com.eventslooped.klyph.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BooleanBehaviorParameterTest {
    private val rule = BooleanBehaviorParameter(Config.empty)

    @Test
    fun `reports a Boolean parameter of a public function`() {
        val findings = rule.lint(
            """
            fun flip(horizontal: Boolean) {}
            fun load(path: String, cached: kotlin.Boolean? = null) {}
            """.trimIndent(),
        )
        assertEquals(listOf("horizontal", "cached"), findings.map { it.entity.ktElement.name })
    }

    @Test
    fun `reports Boolean parameters of public constructors that are not properties`() {
        val findings = rule.lint(
            """
            class Sound(path: String, looping: Boolean) {
                constructor(path: String, looping: Boolean, volume: Float) : this(path, looping)
            }
            """.trimIndent(),
        )
        assertEquals(2, findings.size)
    }

    @Test
    fun `ignores constructors of sealed and enum classes, which are not public by default`() {
        val findings = rule.lint(
            """
            sealed class Shape(filled: Boolean) {
                constructor(filled: Boolean, size: Float) : this(filled)
            }

            enum class Edge(wraps: Boolean) {
                LEFT(false),
                RIGHT(true),
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `ignores constructor properties, overrides and declarations outside the public API`() {
        val findings = rule.lint(
            """
            class KeyPressed(val isRepeat: Boolean, var isHandled: Boolean = false)

            interface Toggle {
                fun set(on: Boolean)
            }

            class Switch : Toggle {
                override fun set(on: Boolean) {}
            }

            private fun hidden(flag: Boolean) {}
            internal fun alsoHidden(flag: Boolean) {}
            class Widget private constructor(flag: Boolean)
            internal class Inner(flag: Boolean)

            fun outer() {
                fun local(flag: Boolean) {}
            }

            fun visit(predicate: (Boolean) -> Boolean) {}
            """.trimIndent(),
        )
        assertEquals(1, findings.size, findings.toString())
        assertTrue("function set" in findings.single().message)
    }
}
