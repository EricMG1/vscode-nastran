package com.nastran.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Port of tests/test_regex.py: every documented entry must be recognised. */
class GrammarTest {
    private val docs = NastranDocs.bundled

    private fun match(key: String, line: String) =
        NastranGrammar.matchAtStart(NastranGrammar.pattern(key), line)?.text

    @Test
    fun bulkCards() {
        for (card in docs.cardNames(Section.BULK)) {
            if (card.startsWith("DTI,")) continue
            val key = if (NastranGrammar.BULK_LABEL_CARDS.any { card.startsWith(it) }) NastranGrammar.BULK_LABEL else NastranGrammar.BULK
            assertEquals(card, match(key, card.padEnd(80)))
            assertEquals(card, match(key, "$card,".padEnd(80)))
            assertEquals(card, match(key, "$card*".padEnd(80)))
            assertEquals(card, match(key, (card.padEnd(7) + "*").padEnd(80)))
        }
    }

    @Test
    fun paramNames() {
        val pattern = NastranGrammar.pattern(NastranGrammar.PARAM)
        for (name in docs.paramNames()) {
            assertEquals(name, NastranGrammar.search(pattern, ("PARAM".padEnd(8) + name.padEnd(8) + "test".padEnd(8)).padEnd(80))?.text)
            assertEquals(name, NastranGrammar.search(pattern, listOf("PARAM", name, "test").joinToString(",").padEnd(80))?.text)
        }
    }

    @Test
    fun caseCommands() {
        for (card in docs.cardNames(Section.CASE)) {
            if ('$' in card || card == "MAXMIN(DEF)") continue
            for (prefix in listOf("", "    ")) {
                assertEquals(card, match(NastranGrammar.CASE, (prefix + card).padEnd(80)))
                for (suffix in listOf("=", "(", ",")) {
                    assertEquals(card, match(NastranGrammar.CASE, (prefix + card + suffix).padEnd(80)))
                }
            }
        }
    }

    @Test
    fun execAndFms() {
        for (card in docs.cardNames(Section.EXEC)) {
            if (card == "SOL 700,ID") continue
            assertEquals(card, match(NastranGrammar.EXEC, card.padEnd(80)))
        }
        for (card in docs.cardNames(Section.FMS)) {
            assertEquals(card, match(NastranGrammar.FMS, card.padEnd(80)))
        }
    }

    @Test
    fun bulkRejectsAssignments() {
        assertNull(match(NastranGrammar.BULK, "LOAD = 1"))
    }

    @Test
    fun grammarMatchesSharedFile() {
        // The resource must be the repository's grammar, not a stale copy
        val root = System.getProperty("nastran.repoRoot") ?: return
        val expected = NastranGrammar.load(File(root, "syntaxes/nastran.json").readText())
        assertEquals(expected.mapValues { it.value.pattern() }, NastranGrammar.patterns.mapValues { it.value.pattern() })
    }
}
