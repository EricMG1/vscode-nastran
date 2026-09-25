package com.nastran.jetbrains.core

/**
 * Minimal JSON reader, enough to load the TextMate grammar shared with the
 * VS Code extension without pulling in a JSON library.
 */
internal class MiniJson private constructor(private val text: String) {
    private var pos = 0

    companion object {
        fun parse(text: String): Any? = MiniJson(text).run {
            val value = readValue()
            skipWhitespace()
            require(pos == text.length) { "Trailing content at $pos" }
            value
        }
    }

    private fun readValue(): Any? {
        skipWhitespace()
        require(pos < text.length) { "Unexpected end of JSON" }
        return when (val c = text[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c.isDigit()) readNumber() else error("Unexpected '$c' at $pos")
        }
    }

    private fun readObject(): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        pos++
        skipWhitespace()
        if (text[pos] == '}') { pos++; return result }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            result[key] = readValue()
            skipWhitespace()
            if (text[pos] == ',') { pos++; continue }
            expect('}')
            return result
        }
    }

    private fun readArray(): List<Any?> {
        val result = ArrayList<Any?>()
        pos++
        skipWhitespace()
        if (text[pos] == ']') { pos++; return result }
        while (true) {
            result.add(readValue())
            skipWhitespace()
            if (text[pos] == ',') { pos++; continue }
            expect(']')
            return result
        }
    }

    private fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            val c = text[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    when (val e = text[pos++]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> { sb.append(text.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun readNumber(): Double {
        val start = pos
        while (pos < text.length && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
        return text.substring(start, pos).toDouble()
    }

    private fun literal(word: String, value: Any?): Any? {
        require(text.startsWith(word, pos)) { "Expected $word at $pos" }
        pos += word.length
        return value
    }

    private fun expect(c: Char) {
        require(pos < text.length && text[pos] == c) { "Expected '$c' at $pos" }
        pos++
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }
}
