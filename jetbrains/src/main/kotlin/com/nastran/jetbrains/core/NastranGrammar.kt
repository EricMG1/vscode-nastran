package com.nastran.jetbrains.core

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The per-section keyword regexes from the TextMate grammar
 * (syntaxes/nastran.json), keyed like REGEX_KEY in utils/parse_file.py:
 * NASTRAN, SECTION, EXEC, FMS, BULK, BULK_LABEL, CASE and PARAM.
 */
object NastranGrammar {
    const val NASTRAN = "NASTRAN"
    const val SECTION = "SECTION"
    const val EXEC = "EXEC"
    const val FMS = "FMS"
    const val BULK = "BULK"
    const val BULK_LABEL = "BULK_LABEL"
    const val CASE = "CASE"
    const val PARAM = "PARAM"

    /** Cards whose remaining fields form one free-text label (see server.py). */
    val BULK_LABEL_CARDS = listOf(
        "MONSUMT", "MONSUM1", "MONSUM", "MONPNT3", "MONPNT2", "MONPNT1", "MONGRP", "MONDSP1", "MONCNCM",
    )

    val patterns: Map<String, Pattern> by lazy { load(readGrammar()) }

    fun pattern(key: String): Pattern = patterns[key] ?: error("No grammar pattern for $key")

    fun patternFor(section: Section): Pattern = pattern(section.name)

    /** Python `re.match`: the match must start at the beginning of [line]. */
    fun matchAtStart(pattern: Pattern, line: CharSequence): Match? {
        val m = pattern.matcher(line)
        return if (m.lookingAt()) m.firstGroup() else null
    }

    /** Python `re.search`: the match may start anywhere in [line]. */
    fun search(pattern: Pattern, line: CharSequence): Match? {
        val m = pattern.matcher(line)
        return if (m.find()) m.firstGroup() else null
    }

    /** The first capture group of a match, which is the keyword itself. */
    data class Match(val text: String, val start: Int, val end: Int)

    private fun Matcher.firstGroup(): Match? {
        if (groupCount() < 1) return null
        val text = group(1)
        if (text.isNullOrEmpty()) return null
        return Match(text, start(1), end(1))
    }

    internal fun load(json: String): Map<String, Pattern> {
        @Suppress("UNCHECKED_CAST")
        val root = MiniJson.parse(json) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val repository = root["repository"] as Map<String, Map<String, Any?>>
        return repository.mapNotNull { (key, rule) ->
            val match = rule["match"] as? String ?: return@mapNotNull null
            key.uppercase() to Pattern.compile(match)
        }.toMap()
    }

    private fun readGrammar(): String {
        val stream = NastranGrammar::class.java.getResourceAsStream("/nastran.json")
            ?: error("nastran.json is missing from the plugin resources")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
