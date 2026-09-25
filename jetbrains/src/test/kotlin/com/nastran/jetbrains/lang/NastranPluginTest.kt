package com.nastran.jetbrains.lang

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nastran.jetbrains.docs.NastranDocumentationProvider
import com.nastran.jetbrains.psi.NastranIncludePath

class NastranPluginTest : BasePlatformTestCase() {
    private val deck = """
        |${'$'} Test deck
        |SOL 101
        |CEND
        |SUBCASE 1
        |  SPC = 1
        |BEGIN BULK
        |PARAM   POST    -1
        |GRID    1               0.0     0.0     0.0
        |GRID*   2                               1.0             -2.0            +
        |*+      3.0
        |CQUAD4,1,1,1,2,3,4
        |INCLUDE 'mesh.bdf'
        |ENDDATA
        |""".trimMargin()

    fun testFileTypeAndHighlighting() {
        myFixture.addFileToProject("mesh.bdf", "GRID    3\n")
        val file = myFixture.configureByText("deck.bdf", deck)
        assertEquals(NastranFileType, file.fileType)
        // Runs the lexer, parser and annotators; fails on any exception or bad annotation range
        myFixture.doHighlighting()
    }

    fun testMissingIncludeIsReported() {
        myFixture.configureByText("deck.bdf", "INCLUDE 'nowhere.bdf'\n")
        assertTrue(myFixture.doHighlighting().any { it.description == "Include file not found" })
    }

    fun testIncludeNavigation() {
        myFixture.addFileToProject("sub/mesh.bdf", "GRID    3\n")
        val file = myFixture.configureByText("deck.bdf", "BEGIN BULK\nINCLUDE 'sub/me<caret>sh.bdf'\nENDDATA\n")
        val path = PsiTreeUtil.findChildOfType(file, NastranIncludePath::class.java)
        assertNotNull(path)
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
        assertTrue(target is PsiFile)
        assertEquals("mesh.bdf", (target as PsiFile).name)
    }

    fun testIncludeContinuedOnNextLine() {
        myFixture.addFileToProject("sub/mesh.bdf", "GRID    3\n")
        myFixture.configureByText("deck.bdf", "INCLUDE 'su<caret>b/\n   mesh.bdf'\n")
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
        assertEquals("mesh.bdf", (target as? PsiFile)?.name)
    }

    fun testHoverDocumentation() {
        myFixture.configureByText("deck.bdf", "GR<caret>ID    1\n")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val doc = NastranDocumentationProvider().generateDoc(element, element)
        assertNotNull(doc)
        assertTrue(doc!!.contains("Grid Point"))
    }

    fun testParamDocumentation() {
        myFixture.configureByText("deck.bdf", "PARAM   PO<caret>ST    -1\n")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val doc = NastranDocumentationProvider().generateDoc(element, element)
        assertNotNull(doc)
        assertTrue(doc!!.contains("POST"))
    }

    fun testCardCompletion() {
        myFixture.configureByText("deck.bdf", "CQUA<caret>\n")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        assertNotNull(items)
        assertTrue(items!!.contains("CQUAD4"))
    }

    fun testCompletionInsertsTemplate() {
        myFixture.configureByText("deck.bdf", "CQUAD4<caret>\n")
        myFixture.completeBasic()
        (myFixture.lookup as LookupImpl?)?.let { lookup ->
            lookup.currentItem = lookup.items.first { it.lookupString == "CQUAD4" }
            myFixture.finishLookup('\n')
        }
        assertTrue(myFixture.editor.document.text.startsWith("CQUAD4  EID     PID     G1"))
    }

    fun testParamCompletion() {
        myFixture.configureByText("deck.bdf", "PARAM   AUTOSP<caret>\n")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        if (items != null) assertTrue(items.all { it.startsWith("AUTOSP") }) else assertTrue(myFixture.editor.document.text.startsWith("PARAM   AUTOSPC"))
    }

    fun testLineComment() {
        myFixture.configureByText("deck.bdf", "GRID<caret>    1\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("\$GRID    1\n")
    }
}
