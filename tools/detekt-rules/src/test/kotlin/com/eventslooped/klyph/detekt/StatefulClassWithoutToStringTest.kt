package com.eventslooped.klyph.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatefulClassWithoutToStringTest {
    private val rule = StatefulClassWithoutToString(Config.empty)

    @Test
    fun `reports public classes that hold var state without toString`() {
        val findings = rule.lint(
            """
            class Counter {
                var count: Int = 0
            }

            class Position(var x: Float, var y: Float)

            class Cache {
                private var entries: Int = 0
                lateinit var name: String
            }

            class Outer {
                class Nested {
                    var depth: Int = 0
                        private set
                }
            }
            """.trimIndent(),
        )
        assertEquals(listOf("Counter", "Position", "Cache", "Nested"), findings.map { it.entity.ktElement.name })
    }

    @Test
    fun `accepts a class that overrides toString`() {
        val findings = rule.lint(
            """
            class Counter {
                var count: Int = 0

                override fun toString(): String = "Counter(count=${'$'}count)"
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `ignores classes without var state and declarations outside the public API`() {
        val findings = rule.lint(
            """
            class Point(val x: Float, val y: Float)

            class View(private val target: IntArray) {
                var first: Int
                    get() = target[0]
                    set(value) {
                        target[0] = value
                    }
            }

            abstract class Shape {
                abstract var size: Float
            }

            interface Mutable {
                var value: Int
            }

            enum class Mode {
                ON;

                var count: Int = 0
            }

            data class Pair(var first: Int, var second: Int)

            private class Hidden {
                var count: Int = 0
            }

            internal class AlsoHidden(var count: Int)
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `counts a var whose accessors use the backing field`() {
        val findings = rule.lint(
            """
            class Volume {
                var level: Float = 1f
                    set(value) {
                        field = value.coerceIn(0f, 1f)
                    }
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
    }
}
