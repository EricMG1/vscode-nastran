package com.nastran.jetbrains.core

import java.util.regex.Pattern

/**
 * The include hierarchy of a root deck, with the deck flattened in include
 * order so the Executive, Case Control and Bulk Data sections can be found
 * across files. Port of TreeDataProvider in client/src/treeview.ts and
 * get_section in utils/parse_file.py.
 */
class IncludeTree private constructor(
    val root: Node,
    /** Index in the flattened deck of each line of each file (first occurrence). */
    private val globalLines: Map<String, IntArray>,
    val cendLine: Int?,
    val beginBulkLine: Int?,
) {
    data class Node(
        val path: String,
        /** Name as written in the INCLUDE statement (the file name for the root). */
        val label: String,
        val exists: Boolean,
        val children: List<Node>,
        /** Line of the INCLUDE statement in the parent file, -1 for the root. */
        val includeLine: Int = -1,
    )

    /** Reads files for the tree; returns null when a file does not exist. */
    fun interface FileReader {
        fun readLines(path: String): List<String>?
    }

    /** Every existing file in the tree, root first. */
    val files: List<String> get() = globalLines.keys.toList()

    operator fun contains(path: String): Boolean = path in globalLines

    /**
     * The section line [line] of [path] belongs to. Needs both CEND and
     * BEGIN BULK in the deck, otherwise everything is treated as Bulk Data.
     */
    fun sectionAt(path: String, line: Int, lineText: String): Section {
        val cend = cendLine ?: return Section.BULK
        val bulk = beginBulkLine ?: return Section.BULK
        val global = globalLines[path]?.getOrNull(line) ?: line
        return when {
            global <= cend -> if (NastranGrammar.matchAtStart(NastranGrammar.pattern(NastranGrammar.EXEC), lineText) != null) Section.EXEC else Section.FMS
            global <= bulk -> Section.CASE
            else -> Section.BULK
        }
    }

    companion object {
        private val CEND = Pattern.compile("(?i)^CEND")
        private val BEGIN_BULK = Pattern.compile("(?i)^BEGIN\\s+BULK")

        fun build(rootPath: String, reader: FileReader, exists: (String) -> Boolean): IncludeTree {
            val globalLines = LinkedHashMap<String, IntArray>()
            var total = 0
            var cend: Int? = null
            var beginBulk: Int? = null

            fun visit(path: String, label: String, includeLine: Int, stack: Set<String>): Node {
                val lines = reader.readLines(path) ?: return Node(path, label, false, emptyList(), includeLine)
                // A file included twice keeps the positions of its first inclusion
                val positions = if (path in globalLines) IntArray(lines.size) else IntArray(lines.size).also { globalLines[path] = it }
                val includes = IncludeParser.findIncludes(lines).associateBy { it.line }
                val children = ArrayList<Node>()
                var i = 0
                while (i < lines.size) {
                    val line = lines[i]
                    if (cend == null && CEND.matcher(line).lookingAt()) cend = total
                    if (beginBulk == null && BEGIN_BULK.matcher(line).lookingAt()) beginBulk = total
                    positions[i] = total++
                    val include = includes[i]
                    if (include != null) {
                        // Count the rest of the statement, then splice in the included file
                        for (j in i + 1..minOf(include.lastLine, lines.size - 1)) positions[j] = total++
                        val target = IncludeParser.candidates(include.path, path, rootPath).firstOrNull(exists)
                        children += when {
                            target == null -> Node(
                                IncludeParser.candidates(include.path, path, rootPath).firstOrNull() ?: include.path,
                                include.path, false, emptyList(), i,
                            )
                            target in stack -> Node(target, include.path, true, emptyList(), i)
                            else -> visit(target, include.path, i, stack + target)
                        }
                        i = include.lastLine + 1
                        continue
                    }
                    i++
                }
                return Node(path, label, true, children, includeLine)
            }

            val name = rootPath.substringAfterLast('/').substringAfterLast('\\')
            val root = visit(rootPath, name, -1, setOf(rootPath))
            return IncludeTree(root, globalLines, cend, beginBulk)
        }
    }
}
