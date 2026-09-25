package com.nastran.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncludeTest {
    @Test
    fun singleLine() {
        val include = IncludeParser.parse(listOf("INCLUDE 'mesh/grid.bdf'"), 0)!!
        assertEquals("mesh/grid.bdf", include.path)
        assertEquals(9, include.pathStart)
        assertEquals(22, include.pathEnd)
        assertEquals(0, include.lastLine)
    }

    @Test
    fun abbreviatedAndOtherQuotes() {
        assertEquals("a.bdf", IncludeParser.parse(listOf("incl \"a.bdf\""), 0)?.path)
        assertEquals("b.bdf", IncludeParser.parse(listOf("INCL `b.bdf`"), 0)?.path)
    }

    @Test
    fun continuedOnNextLine() {
        val include = IncludeParser.parse(listOf("INCLUDE '/very/long/", "   path/file.bdf'"), 0)!!
        assertEquals("/very/long/path/file.bdf", include.path)
        assertEquals(1, include.lastLine)
    }

    @Test
    fun notIncludes() {
        assertNull(IncludeParser.parse(listOf("  INCLUDE 'x'"), 0))
        assertNull(IncludeParser.parse(listOf("GRID    1"), 0))
    }

    @Test
    fun candidates() {
        assertEquals(
            listOf("/deck/sub/a.bdf", "/deck/a.bdf"),
            IncludeParser.candidates("a.bdf", "/deck/sub/b.bdf", "/deck/main.bdf"),
        )
        assertEquals(listOf("/abs/a.bdf"), IncludeParser.candidates("/abs/a.bdf", "/deck/main.bdf", null))
    }

    private val files = mapOf(
        "/deck/main.bdf" to listOf("SOL 101", "CEND", "INCLUDE 'case.inc'", "BEGIN BULK", "INCLUDE 'mesh.bdf'", "INCLUDE 'gone.bdf'", "ENDDATA"),
        "/deck/case.inc" to listOf("SUBCASE 1", "  LOAD = 1"),
        "/deck/mesh.bdf" to listOf("GRID    1", "INCLUDE 'sub/more.bdf'"),
        "/deck/sub/more.bdf" to listOf("GRID    2", "INCLUDE 'more.bdf'"),
    )

    private fun tree() = IncludeTree.build("/deck/main.bdf", { files[it] }, { it in files })

    @Test
    fun hierarchy() {
        val root = tree().root
        assertEquals("main.bdf", root.label)
        assertEquals(listOf("case.inc", "mesh.bdf", "gone.bdf"), root.children.map { it.label })
        assertFalse(root.children[2].exists)
        val more = root.children[1].children.single()
        assertEquals("/deck/sub/more.bdf", more.path)
        // A file including itself is listed once and not expanded
        assertEquals(emptyList<IncludeTree.Node>(), more.children.single().children)
        assertEquals(listOf("/deck/main.bdf", "/deck/case.inc", "/deck/mesh.bdf", "/deck/sub/more.bdf"), tree().files)
    }

    @Test
    fun sections() {
        val tree = tree()
        assertEquals(1, tree.cendLine)
        assertEquals(5, tree.beginBulkLine)
        assertEquals(Section.EXEC, tree.sectionAt("/deck/main.bdf", 0, "SOL 101"))
        assertEquals(Section.CASE, tree.sectionAt("/deck/case.inc", 1, "  LOAD = 1"))
        assertEquals(Section.BULK, tree.sectionAt("/deck/mesh.bdf", 0, "GRID    1"))
        assertEquals(Section.BULK, tree.sectionAt("/deck/main.bdf", 6, "ENDDATA"))
        // BEGIN BULK comes after the included case control, so it is still Case Control
        assertEquals(Section.CASE, tree.sectionAt("/deck/main.bdf", 3, "BEGIN BULK"))
        assertEquals(Section.BULK, tree.sectionAt("/deck/main.bdf", 4, "INCLUDE 'mesh.bdf'"))
        assertTrue("/deck/case.inc" in tree)
    }

    @Test
    fun noMarkersMeansBulk() {
        val tree = IncludeTree.build("/x.bdf", { listOf("SOL 101") }, { true })
        assertEquals(Section.BULK, tree.sectionAt("/x.bdf", 0, "SOL 101"))
    }
}
