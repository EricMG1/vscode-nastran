package com.nastran.jetbrains.core

/**
 * Computes which bulk data fields to shade so alternate fields stand out.
 * Port of the semantic token provider in server/server.py.
 */
object FieldSplitter {
    /** Columns past this are ignored, as in the VS Code extension. */
    const val MAX_COLUMN = 108

    private const val SMALL = 8
    private const val LARGE = 16

    /**
     * Ranges (start inclusive, end exclusive) of the shaded fields on line
     * [index]. [lineAt] returns earlier lines, used to find the parent of a
     * `*` continuation line.
     */
    fun shadedFields(index: Int, lineAt: (Int) -> String?): List<IntRange> {
        val line = lineAt(index) ?: return emptyList()
        val trimmed = line.trimStart()
        if (trimmed.isEmpty() || trimmed.startsWith("$") || trimmed.lowercase().startsWith("incl")) return emptyList()
        val upper = trimmed.uppercase()
        return when {
            NastranGrammar.BULK_LABEL_CARDS.any { upper.startsWith(it) } -> {
                // MON* cards: everything after the second field is one label
                val start = SMALL + fieldWidth(index, lineAt)
                if (start < line.length) listOf(start until line.length) else emptyList()
            }
            ',' !in line && '\'' !in line && '\t' !in line -> fixedFields(line, fieldWidth(index, lineAt))
            ',' in line -> delimitedFields(line, ',')
            '\t' in line -> delimitedFields(line, '\t')
            else -> emptyList()
        }
    }

    /** 16 for large-field cards (a `*` in the name field), else 8. */
    fun fieldWidth(index: Int, lineAt: (Int) -> String?): Int {
        val line = lineAt(index) ?: return SMALL
        if (!line.startsWith("*")) return if (isLarge(line)) LARGE else SMALL
        // Continuation line: the width comes from the card it continues
        var i = index - 1
        while (i >= 0) {
            val parent = lineAt(i) ?: break
            if (!parent.startsWith("*")) return if (isLarge(parent)) LARGE else SMALL
            i--
        }
        return LARGE
    }

    private fun isLarge(line: String): Boolean = '*' in line.take(SMALL)

    private fun fixedFields(line: String, width: Int): List<IntRange> {
        val dollar = line.indexOf('$')
        val limit = minOf(if (dollar >= 0) dollar else line.length, MAX_COLUMN)
        val ranges = ArrayList<IntRange>()
        var start = 0
        var field = 0
        while (start < limit) {
            // Field 1 and the continuation field (column 73+) are always 8 wide
            val w = if (field == 0 || start >= 72) SMALL else width
            val end = minOf(start + w, limit)
            if (field % 2 == 0 && field > 0) ranges.add(start until end)
            start += w
            field++
        }
        return ranges
    }

    private fun delimitedFields(line: String, delimiter: Char): List<IntRange> {
        val dollar = line.indexOf('$')
        val limit = if (dollar >= 0) dollar else line.length
        val ranges = ArrayList<IntRange>()
        var start = 0
        var field = 0
        while (start <= limit) {
            var end = line.indexOf(delimiter, start)
            if (end < 0 || end > limit) end = limit
            if (field % 2 == 0 && field > 0 && end > start) ranges.add(start until end)
            start = end + 1
            field++
        }
        return ranges
    }
}
