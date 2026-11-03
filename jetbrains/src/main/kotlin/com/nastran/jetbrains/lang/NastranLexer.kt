package com.nastran.jetbrains.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.nastran.jetbrains.core.NastranGrammar
import java.util.regex.Pattern

/**
 * Line-oriented lexer. The first word of each line is classified with the
 * keyword regexes of the shared TextMate grammar; the rest of the line is
 * split into text, strings and `$` comments.
 *
 * Only CEND and BEGIN BULK inside the file itself change how lines are
 * classified. Include files usually have neither, so they are lexed in the
 * default state, which tries every section like the TextMate grammar does.
 *
 * State: bit 0 is set inside Case Control, bit 1 is set away from the start
 * of a line. Line starts outside Case Control therefore have state 0, which
 * lets the editor restart lexing at any of them.
 */
class NastranLexer : LexerBase() {
    private data class Token(val start: Int, val end: Int, val type: IElementType, val state: Int)

    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var lineStart = 0
    private var caseControl = false
    private val tokens = ArrayList<Token>()
    private var index = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        caseControl = initialState and CASE != 0
        tokens.clear()
        index = 0
        lineStart = startOffset
        if (initialState and MID_LINE != 0) {
            lexRestOfLine(startOffset, initialState)
        } else {
            lexLine()
        }
    }

    override fun getState(): Int = tokens.getOrNull(index)?.state ?: 0

    override fun getTokenType(): IElementType? = tokens.getOrNull(index)?.type

    override fun getTokenStart(): Int = tokens.getOrNull(index)?.start ?: endOffset

    override fun getTokenEnd(): Int = tokens.getOrNull(index)?.end ?: endOffset

    override fun advance() {
        index++
        if (index >= tokens.size) {
            tokens.clear()
            index = 0
            lexLine()
        }
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    /** Tokenizes the line starting at [lineStart], including its line break. */
    private fun lexLine() {
        if (lineStart >= endOffset) return
        val lineEnd = findLineEnd(lineStart)
        val line = buffer.subSequence(lineStart, lineEnd).toString()
        val startState = if (caseControl) CASE else 0
        val midState = startState or MID_LINE

        var pos = 0
        while (pos < line.length && (line[pos] == ' ' || line[pos] == '\t')) pos++
        if (pos > 0) add(0, pos, NastranTokenTypes.WHITESPACE, startState)

        if (pos < line.length && line[pos] != '$') {
            val keyword = classify(line)
            if (keyword != null && keyword.match.start >= pos) {
                if (keyword.match.start > pos) add(pos, keyword.match.start, NastranTokenTypes.TEXT, stateAt(pos, startState, midState))
                add(keyword.match.start, keyword.match.end, keyword.type, stateAt(keyword.match.start, startState, midState))
                pos = keyword.match.end
                if (keyword.type == NastranTokenTypes.BULK_CARD && keyword.match.text.equals(NastranGrammar.PARAM, ignoreCase = true)) {
                    val param = NastranGrammar.search(NastranGrammar.pattern(NastranGrammar.PARAM), line)
                    if (param != null && param.start >= pos) {
                        lexPlain(line, pos, param.start, midState)
                        add(param.start, param.end, NastranTokenTypes.PARAM_NAME, midState)
                        pos = param.end
                    }
                }
                if (keyword.type == NastranTokenTypes.SECTION_MARKER) {
                    val marker = keyword.match.text.uppercase()
                    if (marker == "CEND") caseControl = true
                    if (marker.startsWith("BEGIN")) caseControl = false
                }
            }
        }
        lexPlain(line, pos, line.length, if (pos == 0) startState else midState, midState)

        val next = if (lineEnd < endOffset) lineEnd + 1 else lineEnd
        if (next > lineEnd) add(line.length, line.length + 1, NastranTokenTypes.EOL, midState)
        lineStart = next
    }

    /** Lexes the remainder of a line when the editor restarts mid-line. */
    private fun lexRestOfLine(start: Int, state: Int) {
        val lineEnd = findLineEnd(start)
        val line = buffer.subSequence(lineStart, lineEnd).toString()
        lexPlain(line, 0, line.length, state, state)
        val next = if (lineEnd < endOffset) lineEnd + 1 else lineEnd
        if (next > lineEnd) add(line.length, line.length + 1, NastranTokenTypes.EOL, state)
        lineStart = next
    }

    private fun stateAt(pos: Int, startState: Int, midState: Int) = if (pos == 0) startState else midState

    /** Splits [line] from [from] to [to] into whitespace, strings, comments and text. */
    private fun lexPlain(line: String, from: Int, to: Int, firstState: Int, midState: Int = firstState) {
        var pos = from
        while (pos < to) {
            val state = if (pos == from) firstState else midState
            val c = line[pos]
            val end = when {
                c == '$' -> to.also { add(pos, to, NastranTokenTypes.COMMENT, state) }
                c == ' ' || c == '\t' -> {
                    var e = pos
                    while (e < to && (line[e] == ' ' || line[e] == '\t')) e++
                    add(pos, e, NastranTokenTypes.WHITESPACE, state)
                    e
                }
                c in QUOTES -> {
                    val close = line.indexOf(c, pos + 1)
                    val e = if (close in 0 until to) close + 1 else to
                    add(pos, e, NastranTokenTypes.STRING, state)
                    e
                }
                else -> {
                    var e = pos
                    while (e < to && line[e] != ' ' && line[e] != '\t' && line[e] != '$' && line[e] !in QUOTES) e++
                    add(pos, e, NastranTokenTypes.TEXT, state)
                    e
                }
            }
            pos = end
        }
    }

    private data class Keyword(val match: NastranGrammar.Match, val type: IElementType)

    /** The keyword starting [line], trying the patterns that apply to the current section. */
    private fun classify(line: String): Keyword? {
        for ((key, type) in if (caseControl) CASE_ORDER else DEFAULT_ORDER) {
            val pattern: Pattern = NastranGrammar.patterns[key] ?: continue
            val match = NastranGrammar.matchAtStart(pattern, line) ?: continue
            // Keywords must be whole words: "IDENT" is not the ID statement
            val next = line.getOrNull(match.end)
            if (next != null && (next.isLetterOrDigit() || next == '_')) continue
            return Keyword(match, type)
        }
        return null
    }

    private fun findLineEnd(from: Int): Int {
        var i = from
        while (i < endOffset && buffer[i] != '\n') i++
        return i
    }

    private fun add(start: Int, end: Int, type: IElementType, state: Int) {
        if (end > start) tokens.add(Token(lineStart + start, lineStart + end, type, state))
    }

    private companion object {
        const val CASE = 1
        const val MID_LINE = 2
        val QUOTES = charArrayOf('\'', '"', '`')

        // Same precedence as the TextMate grammar, with bulk data before the
        // permissive Case Control pattern so bulk-only include files look right.
        val DEFAULT_ORDER = listOf(
            NastranGrammar.SECTION to NastranTokenTypes.SECTION_MARKER,
            NastranGrammar.NASTRAN to NastranTokenTypes.NASTRAN_STATEMENT,
            NastranGrammar.FMS to NastranTokenTypes.FMS_KEYWORD,
            NastranGrammar.BULK_LABEL to NastranTokenTypes.BULK_CARD,
            NastranGrammar.BULK to NastranTokenTypes.BULK_CARD,
            NastranGrammar.EXEC to NastranTokenTypes.EXEC_KEYWORD,
            NastranGrammar.CASE to NastranTokenTypes.CASE_KEYWORD,
        )
        val CASE_ORDER = listOf(
            NastranGrammar.SECTION to NastranTokenTypes.SECTION_MARKER,
            NastranGrammar.CASE to NastranTokenTypes.CASE_KEYWORD,
        )
    }
}
