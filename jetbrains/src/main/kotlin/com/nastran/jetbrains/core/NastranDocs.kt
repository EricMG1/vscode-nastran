package com.nastran.jetbrains.core

import java.util.concurrent.ConcurrentHashMap

/**
 * The MSC Nastran quick-reference pages from utils/docs. Port of
 * utils/read_docs.py.
 *
 * Pages are listed in docs/index.txt (generated at build time) as
 * `SECTION/NAME` or `SECTION/SUBFOLDER/NAME`, e.g. `BULK/PARAM/POST`.
 */
class NastranDocs(private val readResource: (String) -> String?) {
    private data class Page(val section: String, val name: String, val path: String)

    private val pages: List<Page> by lazy {
        (readResource("index.txt") ?: "").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Page(it.substringBefore('/'), it.substringAfterLast('/'), it) }
            .toList()
    }

    private val cache = ConcurrentHashMap<String, String>()

    /** Names of every documented entry in [section], PARAM names included for BULK. */
    fun names(section: Section): List<String> = pages.filter { it.section == section.docsFolder }.map { it.name }

    /** Names of documented entries directly in [section], without the PARAM pages. */
    fun cardNames(section: Section): List<String> =
        pages.filter { it.section == section.docsFolder && it.path.count { c -> c == '/' } == 1 }.map { it.name }

    fun paramNames(): List<String> = pages.filter { it.path.startsWith("BULK/PARAM/") }.map { it.name }

    /**
     * Documentation for [card] in [section], or "" if there is none.
     * [line] is the whole line, used to tell PARAM names from cards.
     */
    fun docs(card: String, section: Section, line: String): String {
        val folder = section.docsFolder
        if (line.uppercase().contains("PARAM") && !card.equals("PARAM", ignoreCase = true)) {
            read("$folder/PARAM/$card")?.let { return it }
        } else {
            read("$folder/$card")?.let { return it }
        }
        // Fall back to the first entry that starts with the (possibly abbreviated) name
        val match = names(section).firstOrNull { it.startsWith(card) && '$' !in it } ?: return ""
        return read("$folder/$match") ?: ""
    }

    /** Documentation for a PARAM name, or "" if there is none. */
    fun paramDocs(name: String): String = read("BULK/PARAM/$name") ?: ""

    private fun read(path: String): String? {
        cache[path]?.let { return it }
        val text = readResource("$path.md")?.replace("\r\n", "\n") ?: return null
        cache[path] = text
        return text
    }

    companion object {
        /** Pages bundled in the plugin jar under /docs. */
        val bundled: NastranDocs by lazy {
            NastranDocs { path ->
                NastranDocs::class.java.getResourceAsStream("/docs/$path")?.use { it.readBytes().toString(Charsets.UTF_8) }
            }
        }

        /**
         * The card template shown by completion: the first ```nastran block
         * without its column ruler. Port of `get_completion_item`.
         */
        fun completionTemplate(docs: String): String {
            val afterFence = docs.split("```nastran\n", limit = 2).getOrNull(1) ?: return ""
            val example = afterFence.split("```\n", limit = 2)[0]
            val out = example.split('\n').drop(1).joinToString("\n").trim() + "\n"
            return if (" See " in out) "" else out
        }
    }
}
