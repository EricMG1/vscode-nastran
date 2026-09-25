package com.nastran.jetbrains.core

/**
 * Finds the Nastran entry under a cursor. Port of the hover logic in
 * server/server.py.
 */
object CardDetector {
    data class Card(
        /** Entry name as written, e.g. "GRID" or the parameter name "POST". */
        val name: String,
        val start: Int,
        val end: Int,
        /** True when [name] is a PARAM name rather than a card. */
        val isParam: Boolean,
    )

    /** The keyword that starts [line], if any, ignoring the cursor. */
    fun leadingCard(line: String, section: Section): Card? {
        val pattern = when {
            section == Section.BULK && isLabelCard(line) -> NastranGrammar.pattern(NastranGrammar.BULK_LABEL)
            else -> NastranGrammar.patternFor(section)
        }
        return NastranGrammar.matchAtStart(pattern, line)?.let { Card(it.text, it.start, it.end, false) }
    }

    /** The PARAM name on a `PARAM` line, if any. */
    fun paramName(line: String): Card? =
        NastranGrammar.search(NastranGrammar.pattern(NastranGrammar.PARAM), line)
            ?.let { Card(it.text, it.start, it.end, true) }

    /**
     * The entry whose name spans [column] on [line] (end inclusive, as in
     * VS Code), or null when the cursor is elsewhere.
     */
    fun cardAt(line: String, section: Section, column: Int): Card? {
        if (line.trimStart().startsWith("$")) return null
        val card = if (section == Section.BULK && isParamLine(line)) {
            val start = line.uppercase().indexOf(NastranGrammar.PARAM)
            if (column in start..start + NastranGrammar.PARAM.length) leadingCard(line, section) else paramName(line)
        } else {
            leadingCard(line, section)
        }
        return card?.takeIf { column in it.start..it.end }
    }

    fun isParamLine(line: String): Boolean = line.trimStart().uppercase().startsWith(NastranGrammar.PARAM)

    fun isLabelCard(line: String): Boolean = line.trimStart().uppercase().startsWith("MON")
}
