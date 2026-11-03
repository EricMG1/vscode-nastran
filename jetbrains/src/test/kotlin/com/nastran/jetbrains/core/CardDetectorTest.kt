package com.nastran.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardDetectorTest {
    @Test
    fun cardUnderCursor() {
        val line = "GRID    2       3       1.0"
        assertEquals("GRID", CardDetector.cardAt(line, Section.BULK, 0)?.name)
        assertEquals("GRID", CardDetector.cardAt(line, Section.BULK, 4)?.name)
        assertNull(CardDetector.cardAt(line, Section.BULK, 10))
    }

    @Test
    fun paramKeywordAndName() {
        val line = "PARAM   POST    -1"
        assertEquals("PARAM", CardDetector.cardAt(line, Section.BULK, 2)?.name)
        val name = CardDetector.cardAt(line, Section.BULK, 9)
        assertEquals("POST", name?.name)
        assertEquals(true, name?.isParam)
        assertEquals("POST", CardDetector.cardAt("PARAM,POST,-1", Section.BULK, 7)?.name)
    }

    @Test
    fun monitorLabels() {
        assertEquals("MONPNT1", CardDetector.cardAt("MONPNT1 NAME    LABEL", Section.BULK, 3)?.name)
    }

    @Test
    fun otherSections() {
        assertEquals("SOL", CardDetector.cardAt("SOL 101", Section.EXEC, 1)?.name)
        assertEquals("SPC", CardDetector.cardAt("  SPC = 1", Section.CASE, 3)?.name)
        assertEquals("ASSIGN", CardDetector.cardAt("ASSIGN OUTPUT2='x.op2'", Section.FMS, 1)?.name)
    }

    @Test
    fun commentsHaveNoCard() {
        assertNull(CardDetector.cardAt("\$ GRID", Section.BULK, 3))
    }
}
