package samples

import kotlin.test.assertEquals

/** Marks a function as a sample. Samples run as tests, so every `@sample` tag in the KDoc stays correct. */
typealias Sample = kotlin.test.Test

/** Asserts that [value] prints as [expected]. */
fun assertPrints(value: Any?, expected: String) {
    assertEquals(expected, value.toString())
}
