package com.nastran.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldSplitterTest {
    private fun shade(vararg lines: String, index: Int = lines.size - 1) =
        FieldSplitter.shadedFields(index) { lines.getOrNull(it) }

    @Test
    fun smallField() {
        val line = "GRID    2       3       1.0     -2.0    3.0             316"
        assertEquals(listOf(16 until 24, 32 until 40, 48 until 56), shade(line))
    }

    @Test
    fun largeField() {
        val line = "GRID*   2                               1.0             -2.0            +"
        assertEquals(listOf(24 until 40, 56 until 72), shade(line))
    }

    @Test
    fun largeFieldContinuation() {
        val parent = "GRID*   2                               1.0             -2.0            +"
        val cont = "*+      3.0                             136"
        assertEquals(listOf(24 until 40), shade(parent, cont))
    }

    @Test
    fun freeField() {
        assertEquals(listOf(7 until 8, 13 until 17), shade("GRID,2,3,1.0,-2.0,3.0"))
    }

    @Test
    fun tabs() {
        assertEquals(listOf(7 until 8), shade("GRID\t2\t3\t1.0"))
    }

    @Test
    fun trailingComment() {
        assertEquals(listOf(16 until 20), shade("GRID    2       3   \$ note"))
    }

    @Test
    fun monitorLabel() {
        assertEquals(listOf(16 until 28), shade("MONPNT1 NAME    A LABEL HERE"))
    }

    @Test
    fun skipsCommentsAndIncludes() {
        assertEquals(emptyList<IntRange>(), shade("\$ GRID    2       3"))
        assertEquals(emptyList<IntRange>(), shade("INCLUDE 'mesh.bdf'"))
    }
}
