package com.nastran.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of tests/test_read_docs.py. */
class DocsTest {
    private val docs = NastranDocs.bundled

    private fun page(path: String): String =
        NastranDocs::class.java.getResourceAsStream("/docs/$path.md")!!.use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun readDocs() {
        assertEquals(page("BULK/GRID"), docs.docs("GRID", Section.BULK, "GRID"))
        assertEquals(page("BULK/PARAM/POST"), docs.docs("POST", Section.BULK, "PARAM,POST"))
        assertEquals(page("CASE/ACCELERATION"), docs.docs("ACCELER", Section.CASE, "ACCELER"))
    }

    @Test
    fun sameNameInTwoSections() {
        assertEquals(page("BULK/ACCEL"), docs.docs("ACCEL", Section.BULK, "ACCEL"))
        assertEquals(page("CASE/ACCELERATION"), docs.docs("ACCEL", Section.CASE, "ACCEL"))
    }

    @Test
    fun completionTemplates() {
        assertEquals(
            "GRID    ID      CP      X1      X2      X3      CD      PS      SEID\n",
            NastranDocs.completionTemplate(docs.docs("GRID", Section.BULK, "GRID")),
        )
        assertEquals("", NastranDocs.completionTemplate(docs.docs("BCBODY", Section.BULK, "BCBODY")))
        assertEquals("", NastranDocs.completionTemplate(docs.docs("TEMPP3", Section.BULK, "TEMPP3")))
    }

    @Test
    fun indexCoversEverySection() {
        for (section in Section.entries) assertTrue(section.name, docs.names(section).isNotEmpty())
        assertTrue("POST" in docs.paramNames())
        assertTrue("POST" !in docs.cardNames(Section.BULK))
    }

    @Test
    fun html() {
        val html = DocsHtml.toHtml(docs.docs("GRID", Section.BULK, "GRID"))
        assertTrue(html.contains("<a href=\"https://nexus.hexagon.com/"))
        assertTrue(html.contains("<pre>\$---1---\$---2---"))
        assertTrue(html.contains("\nGRID    ID      CP"))
        assertTrue(html.contains("<h5>Format:</h5>"))
        assertEquals("<p>a &lt;b&gt; <img src=\"x.jpg\" alt=\"i\"> <a href=\"u\">t</a></p>\n", DocsHtml.toHtml("a <b> ![i](x.jpg) [t](u)"))
    }
}
