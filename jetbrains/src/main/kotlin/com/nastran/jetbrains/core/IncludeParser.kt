package com.nastran.jetbrains.core

import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Finds INCLUDE statements and resolves their paths. Port of getIncludes and
 * getIncludeFilename in client/src/treeview.ts.
 */
object IncludeParser {
    private val QUOTES = charArrayOf('\'', '"', '`')

    data class Include(
        /** Line holding the INCLUDE keyword. */
        val line: Int,
        /** Last line of the statement; the path may continue on the next line. */
        val lastLine: Int,
        val path: String,
        /** Offsets on [line] of the path text, excluding quotes. */
        val pathStart: Int,
        val pathEnd: Int,
    )

    fun isIncludeLine(line: String): Boolean = line.lowercase().startsWith("incl")

    /** Every INCLUDE statement in [lines]. */
    fun findIncludes(lines: List<String>): List<Include> {
        val result = ArrayList<Include>()
        var i = 0
        while (i < lines.size) {
            val include = parse(lines, i)
            if (include != null) {
                result.add(include)
                i = include.lastLine + 1
            } else {
                i++
            }
        }
        return result
    }

    /** The INCLUDE statement starting on line [index], if there is one. */
    fun parse(lines: List<String>, index: Int): Include? {
        val line = lines.getOrNull(index) ?: return null
        if (!isIncludeLine(line)) return null
        val quote = QUOTES.firstOrNull { it in line } ?: return null
        val open = line.indexOf(quote)
        val close = line.indexOf(quote, open + 1)
        if (close >= 0) {
            return Include(index, index, line.substring(open + 1, close), open + 1, close)
        }
        // Path continues on the next line: join the trimmed lines, as VS Code does
        val next = lines.getOrNull(index + 1) ?: ""
        val joined = line.trim() + next.trim()
        val path = joined.split(quote).getOrNull(1) ?: return null
        return Include(index, index + 1, path, open + 1, line.trimEnd().length)
    }

    /**
     * Candidate locations for [path] in the order VS Code tries them: as given,
     * next to the including file, then next to the root deck.
     */
    fun candidates(path: String, includingFile: String, rootFile: String?): List<String> {
        if (path.isBlank()) return emptyList()
        val result = LinkedHashSet<String>()
        fun add(p: Path?) {
            if (p != null) result.add(p.normalize().toString().replace('\\', '/'))
        }
        try {
            val given = Paths.get(path)
            if (given.isAbsolute) add(given)
            add(Paths.get(includingFile).parent?.resolve(path))
            rootFile?.let { add(Paths.get(it).parent?.resolve(path)) }
        } catch (_: InvalidPathException) {
            return emptyList()
        }
        return result.toList()
    }
}
