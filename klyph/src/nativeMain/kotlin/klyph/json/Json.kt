package klyph.json

import klyph.KlyphException

/**
 * Thrown when text is not valid JSON, or when a value read through [JsonObject] is missing or of
 * the wrong type. A syntax error names the line and column; a missing or mistyped value names the
 * member and the context the [JsonObject] was created with.
 */
internal class JsonException(message: String) : KlyphException(message)

/**
 * Parses [text] as one JSON value and returns it: a `Map<String, Any?>` for an object, in the
 * order its members appear, a `List<Any?>` for an array, a `String`, a `Double` for every number,
 * a `Boolean` or `null`. Nothing but whitespace may follow the value.
 *
 * Strict RFC 8259 JSON: no comments, no trailing commas, no single quotes, no leading zeros, no
 * raw control characters inside strings, and a `\u` escape of a surrogate must be one half of a
 * pair. A number too large for a `Double`, such as `1e309`, is rejected rather than read as
 * infinity. Nesting deeper than [MAX_JSON_DEPTH] is rejected so a hostile file cannot exhaust the stack.
 *
 * @throws JsonException if [text] is not one valid JSON value, naming the line and column.
 */
internal fun parseJson(text: String): Any? = JsonParser(text).parseDocument()

/** The deepest nesting of arrays and objects [parseJson] accepts. */
internal const val MAX_JSON_DEPTH: Int = 256

/** A recursive-descent parser over one string, which [parseJson] creates for each call. */
@Suppress("TooManyFunctions") // One function per rule of the JSON grammar reads more plainly than fewer, longer ones.
private class JsonParser(private val text: String) {
    private var index = 0
    private var depth = 0

    fun parseDocument(): Any? {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (index < text.length) fail("Unexpected '${text[index]}' after the end of the JSON value")
        return value
    }

    private fun parseValue(): Any? {
        if (index >= text.length) fail("The text ends where a value was expected")
        return when (val c = text[index]) {
            '{' -> nested { parseObject() }
            '[' -> nested { parseArray() }
            '"' -> parseString()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c in '0'..'9') parseNumber() else fail("Unexpected '$c' where a value was expected")
        }
    }

    private inline fun <T> nested(parse: () -> T): T {
        if (++depth > MAX_JSON_DEPTH) fail("Arrays and objects nest deeper than $MAX_JSON_DEPTH levels")
        val value = parse()
        depth--
        return value
    }

    private fun parseObject(): Map<String, Any?> {
        val members = LinkedHashMap<String, Any?>()
        index++
        skipWhitespace()
        if (peek() == '}') {
            index++
            return members
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("Expected '\"' to start a member name")
            val name = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            members[name] = parseValue()
            skipWhitespace()
            if (peek() == ',') index++ else break
        }
        expect('}', "',' or '}'")
        return members
    }

    private fun parseArray(): List<Any?> {
        val items = ArrayList<Any?>()
        index++
        skipWhitespace()
        if (peek() == ']') {
            index++
            return items
        }
        while (true) {
            skipWhitespace()
            items.add(parseValue())
            skipWhitespace()
            if (peek() == ',') index++ else break
        }
        expect(']', "',' or ']'")
        return items
    }

    private fun parseString(): String {
        val start = index
        // Most strings hold no escape, so look for the closing quote first and cut the string out whole.
        var end = start + 1
        while (end < text.length) {
            val c = text[end]
            if (c == '"') {
                index = end + 1
                return text.substring(start + 1, end)
            }
            if (c == '\\' || c < ' ') break
            end++
        }
        val builder = StringBuilder()
        builder.appendRange(text, start + 1, end)
        index = end
        while (true) {
            if (index >= text.length) fail("The text ends inside the string that starts", start)
            val c = text[index]
            when {
                c == '"' -> break
                c == '\\' -> parseEscape(builder)
                c < ' ' -> fail("Control character U+${c.code.toString(16).padStart(4, '0')} inside a string; escape it")
                else -> {
                    builder.append(c)
                    index++
                }
            }
        }
        index++
        return builder.toString()
    }

    /** Appends the character of the escape at [index], a backslash, to [builder] and moves past it. */
    private fun parseEscape(builder: StringBuilder) {
        val start = index
        index++
        if (index >= text.length) fail("The text ends inside an escape", start)
        val c = text[index]
        index++
        if (c != 'u') {
            builder.append(simpleEscape(c) ?: fail("Unknown escape '\\$c'", start))
            return
        }
        val unit = readHex(start)
        if (unit.isLowSurrogate()) fail("A low surrogate escape has no high surrogate before it", start)
        builder.append(unit)
        if (unit.isHighSurrogate()) {
            val low = if (text.startsWith("\\u", index)) {
                index += 2
                readHex(index - 2)
            } else {
                null
            }
            if (low == null || !low.isLowSurrogate()) fail("A high surrogate escape is not followed by a low surrogate escape", start)
            builder.append(low)
        }
    }

    /** Reads the four hex digits at [index] as one UTF-16 code unit; [start] is the escape's backslash, for errors. */
    private fun readHex(start: Int): Char {
        if (index + 4 > text.length) fail("The text ends inside a \\u escape", start)
        var value = 0
        for (k in 0 until 4) {
            val digit = text[index + k].digitToIntOrNull(16) ?: fail("A \\u escape needs four hex digits", start)
            value = value * 16 + digit
        }
        index += 4
        return value.toChar()
    }

    private fun parseNumber(): Double {
        val start = index
        val isNegative = peek() == '-'
        if (isNegative) index++
        val digitsStart = index
        when {
            peek() == '0' -> index++
            peek() in '1'..'9' -> skipDigits()
            else -> fail("A number needs a digit after '-'", start)
        }
        val digitsEnd = index
        val isWhole = !skipFractionAndExponent(start)
        if (isWhole && digitsEnd - digitsStart <= MAX_LONG_DIGITS) {
            // A whole number this short fits a Long, and converting it rounds as parsing its text would.
            var magnitude = 0L
            for (i in digitsStart until digitsEnd) magnitude = magnitude * 10 + (text[i] - '0')
            return if (isNegative) -magnitude.toDouble() else magnitude.toDouble()
        }
        val number = text.substring(start, index)
        val value = number.toDouble()
        if (!value.isFinite()) fail("The number $number is too large for a Double", start)
        return value
    }

    /**
     * Moves past the fraction and the exponent of the number that starts at [start], if it has
     * them, and returns `true` if it had either.
     */
    private fun skipFractionAndExponent(start: Int): Boolean {
        val end = index
        if (peek() == '.') {
            index++
            if (peek() !in '0'..'9') fail("A number needs a digit after '.'", start)
            skipDigits()
        }
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') index++
            if (peek() !in '0'..'9') fail("A number needs a digit in its exponent", start)
            skipDigits()
        }
        return index != end
    }

    private fun skipDigits() {
        while (peek() in '0'..'9') index++
    }

    private fun literal(word: String, value: Boolean?): Boolean? {
        if (!text.startsWith(word, index)) fail("Unexpected '${text[index]}' where a value was expected")
        index += word.length
        return value
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index] in WHITESPACE) index++
    }

    /** Returns the character at [index], or a NUL character at the end of the text, which no rule accepts. */
    private fun peek(): Char = if (index < text.length) text[index] else '\u0000'

    private fun expect(c: Char, what: String = "'$c'") {
        if (peek() != c) {
            if (index >= text.length) fail("The text ends where $what was expected")
            fail("Expected $what but found '${text[index]}'")
        }
        index++
    }

    /** Throws a [JsonException] with [problem] and the line and column of [at]. */
    private fun fail(problem: String, at: Int = index): Nothing {
        var line = 1
        var lineStart = 0
        for (i in 0 until minOf(at, text.length)) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        throw JsonException("$problem at line $line, column ${at - lineStart + 1}.")
    }
}

/**
 * The members of one JSON object with typed reads that fail with a message naming the member and
 * [context], such as "Layer 'ground' of level.tmj". [fix] ends every message, the sentence that
 * tells the reader what to do about it.
 */
@Suppress("TooManyFunctions") // One typed read, and its OrNull sibling, per JSON type the Tiled reader needs.
internal class JsonObject(private val members: Map<String, Any?>, val context: String, private val fix: String) {
    /** Returns `true` if the object has a member called [name], whatever its value. */
    fun has(name: String): Boolean = name in members

    /**
     * Returns the whole number in member [name].
     *
     * @throws JsonException if it is missing, not a number or not a whole number in the range of an `Int`.
     */
    fun int(name: String): Int = intOrNull(name) ?: missing(name, "a whole number")

    /**
     * Returns the whole number in member [name], or `null` if there is no such member or it is `null`.
     *
     * @throws JsonException if it is not a whole number in the range of an `Int`.
     */
    fun intOrNull(name: String): Int? {
        val value = members[name] ?: return null
        val number = value as? Double
        if (number == null || !isInt(number)) {
            wrongType(name, value, "a whole number")
        }
        return number.toInt()
    }

    /**
     * Returns the number in member [name], or `null` if there is no such member or it is `null`.
     *
     * @throws JsonException if it is not a number.
     */
    fun doubleOrNull(name: String): Double? {
        val value = members[name] ?: return null
        return value as? Double ?: wrongType(name, value, "a number")
    }

    /**
     * Returns the number in member [name] as a `Float`, or [default] if there is no such member or it is `null`.
     *
     * @throws JsonException if it is not a number, or it is too large for a `Float`.
     */
    fun floatOrDefault(name: String, default: Float): Float {
        val number = doubleOrNull(name) ?: return default
        val value = number.toFloat()
        if (!value.isFinite()) wrongType(name, number, "a number in the range of a Float")
        return value
    }

    /**
     * Returns the string in member [name].
     *
     * @throws JsonException if it is missing or not a string.
     */
    fun string(name: String): String = stringOrNull(name) ?: missing(name, "a string")

    /**
     * Returns the string in member [name], or `null` if there is no such member or it is `null`.
     *
     * @throws JsonException if it is not a string.
     */
    fun stringOrNull(name: String): String? {
        val value = members[name] ?: return null
        return value as? String ?: wrongType(name, value, "a string")
    }

    /**
     * Returns the boolean in member [name], or `null` if there is no such member or it is `null`.
     *
     * @throws JsonException if it is not `true` or `false`.
     */
    fun booleanOrNull(name: String): Boolean? {
        val value = members[name] ?: return null
        return value as? Boolean ?: wrongType(name, value, "true or false")
    }

    /**
     * Returns the raw value of member [name] as [parseJson] made it, or `null` if there is none.
     */
    fun valueOrNull(name: String): Any? = members[name]

    /**
     * Returns the objects in the array in member [name], an empty list if there is no such member,
     * each with [contextOf] its position and the object itself as its context.
     *
     * @throws JsonException if the member is not an array, or an item is not an object.
     */
    fun objects(name: String, contextOf: (index: Int, item: JsonObject) -> String): List<JsonObject> {
        val value = members[name] ?: return emptyList()
        val items = value as? List<*> ?: wrongType(name, value, "an array")
        return items.mapIndexed { index, item ->
            @Suppress("UNCHECKED_CAST")
            val itemMembers = item as? Map<String, Any?> ?: wrongType("$name[$index]", item, "an object")
            val bare = JsonObject(itemMembers, "$context, $name[$index],", fix)
            JsonObject(itemMembers, contextOf(index, bare), fix)
        }
    }

    /** Throws a [JsonException] whose message starts with [context], says [problem] and ends with the fix. */
    fun fail(problem: String): Nothing = throw JsonException("$context $problem; $fix.")

    private fun missing(name: String, kind: String): Nothing = fail("has no '$name', which must be $kind")

    private fun wrongType(name: String, value: Any?, kind: String): Nothing =
        fail("has '$name' set to ${describe(value)}, which must be $kind")

    override fun toString(): String = "JsonObject(context=$context, members=${members.keys})"
}

/** Returns the character a backslash and [c] stand for in a JSON string, or `null` if [c] is not a one-character escape. */
private fun simpleEscape(c: Char): Char? = when (c) {
    '"' -> '"'
    '\\' -> '\\'
    '/' -> '/'
    'b' -> '\b'
    'f' -> '\u000C'
    'n' -> '\n'
    'r' -> '\r'
    't' -> '\t'
    else -> null
}

/** The most digits a whole number may have for [JsonParser] to add them up in a `Long`, which holds every 18-digit number. */
private const val MAX_LONG_DIGITS = 18

/** The four characters JSON allows between tokens. */
private const val WHITESPACE = " \n\r\t"

/** Returns `true` if [number] is a whole number in the range of an `Int`. */
private fun isInt(number: Double): Boolean =
    number == kotlin.math.floor(number) && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE

/** Returns a short description of a value [parseJson] made, for messages: the number, the quoted string, or its kind. */
private fun describe(value: Any?): String = when (value) {
    null -> "null"
    is Double -> if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
    is String -> if (value.length <= 20) "\"$value\"" else "\"${value.take(20)}...\""
    is Boolean -> value.toString()
    is List<*> -> "an array"
    else -> "an object"
}
