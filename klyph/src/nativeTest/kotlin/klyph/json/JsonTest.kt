package klyph.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonTest {
    private fun failure(text: String): String = assertFailsWith<JsonException> { parseJson(text) }.message.orEmpty()

    @Test
    fun parsesNestedObjectsAndArraysInOrder() {
        val value = parseJson("""{"b": [1, {"c": null}, []], "a": {}, "d": true, "e": false}""")
        val expected = mapOf(
            "b" to listOf(1.0, mapOf("c" to null), emptyList<Any?>()),
            "a" to emptyMap<String, Any?>(),
            "d" to true,
            "e" to false,
        )
        assertEquals(expected, value)
        assertEquals(listOf("b", "a", "d", "e"), (value as Map<*, *>).keys.toList())
    }

    @Test
    fun parsesNumbers() {
        assertEquals(listOf(0.0, -0.0, 12.0, -3.5, 1e3, 2.5e-2, 4294967295.0), parseJson("[0, -0, 12, -3.5, 1e3, 2.5E-2, 4294967295]"))
    }

    @Test
    fun rejectsMalformedNumbers() {
        for (text in listOf("01", "-", "1.", ".5", "1e", "+1", "0x10")) assertFailsWith<JsonException>(text) { parseJson(text) }
    }

    @Test
    fun parsesEscapes() {
        assertEquals("quote\" slash\\ / \b\u000C\n\r\t A é", parseJson(""""quote\" slash\\ \/ \b\f\n\r\t A é""""))
    }

    @Test
    fun parsesSurrogatePairsAndRawNonAsciiText() {
        assertEquals("😀 and 😀 and é", parseJson(""""😀 and 😀 and é""""))
    }

    @Test
    fun rejectsUnpairedSurrogates() {
        assertTrue("high surrogate" in failure(""""\ud83d x""""), failure(""""\ud83d x""""))
        assertTrue("low surrogate" in failure(""""\ude00""""))
        assertTrue("low surrogate" in failure(""""\ud83dA""""))
    }

    @Test
    fun rejectsBadEscapesAndControlCharacters() {
        assertTrue("Unknown escape" in failure(""""\x""""))
        assertTrue("four hex digits" in failure(""""\u12g4""""))
        assertTrue("Control character" in failure("\"a\nb\""))
    }

    @Test
    fun parsesWhitespaceAroundTheValue() {
        assertNull(parseJson(" \n\t null \r\n"))
    }

    @Test
    fun rejectsTruncatedInput() {
        assertEquals("The text ends where ',' or '}' was expected at line 1, column 9.", failure("""{"a": 1 """))
        assertEquals("The text ends inside the string that starts at line 1, column 2.", failure("""["abc"""))
        assertEquals("The text ends where a value was expected at line 1, column 1.", failure(""))
        assertEquals("The text ends where a value was expected at line 1, column 6.", failure("""{"a":"""))
    }

    @Test
    fun rejectsTrailingGarbage() {
        assertEquals("Unexpected 'x' after the end of the JSON value at line 2, column 3.", failure("{}\n  x"))
        assertTrue("after the end" in failure("[1] [2]"))
    }

    @Test
    fun errorsNameLineAndColumn() {
        assertEquals("Expected ',' or ']' but found '2' at line 3, column 5.", failure("[\n  1,\n  1 2]"))
        assertEquals("Unexpected '}' where a value was expected at line 1, column 7.", failure("""{"a": }"""))
        assertEquals("Expected ',' or '}' but found '\"' at line 1, column 10.", failure("""{"a": 1  "b": 2}"""))
    }

    @Test
    fun rejectsTrailingCommasAndComments() {
        assertTrue("where a value was expected" in failure("[1, ]"))
        assertTrue("member name" in failure("""{"a": 1,}"""))
        assertTrue("where a value was expected" in failure("// no\n1"))
    }

    @Test
    fun rejectsDeepNesting() {
        val deep = "[".repeat(MAX_JSON_DEPTH + 1) + "]".repeat(MAX_JSON_DEPTH + 1)
        assertTrue("deeper than $MAX_JSON_DEPTH" in failure(deep))
        val allowed = "[".repeat(MAX_JSON_DEPTH) + "]".repeat(MAX_JSON_DEPTH)
        parseJson(allowed)
    }

    @Test
    fun typedReadsNameTheMemberAndContext() {
        @Suppress("UNCHECKED_CAST")
        val members = parseJson("""{"width": 2.5, "name": 3, "count": 4, "visible": "yes"}""") as Map<String, Any?>
        val json = JsonObject(members, "Layer 'ground' of level.tmj", "save it again")
        assertEquals(4, json.int("count"))
        assertNull(json.intOrNull("missing"))
        assertEquals(
            "Layer 'ground' of level.tmj has 'width' set to 2.5, which must be a whole number; save it again.",
            assertFailsWith<JsonException> { json.int("width") }.message,
        )
        assertEquals(
            "Layer 'ground' of level.tmj has no 'height', which must be a whole number; save it again.",
            assertFailsWith<JsonException> { json.int("height") }.message,
        )
        assertEquals(
            "Layer 'ground' of level.tmj has 'name' set to 3, which must be a string; save it again.",
            assertFailsWith<JsonException> { json.string("name") }.message,
        )
        assertEquals(
            "Layer 'ground' of level.tmj has 'visible' set to \"yes\", which must be true or false; save it again.",
            assertFailsWith<JsonException> { json.booleanOrNull("visible") }.message,
        )
    }
}
