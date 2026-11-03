package com.nastran.jetbrains.lang

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

object NastranColors {
    @JvmField val COMMENT = createTextAttributesKey("NASTRAN_COMMENT", Default.LINE_COMMENT)
    @JvmField val SECTION_MARKER = createTextAttributesKey("NASTRAN_SECTION_MARKER", Default.METADATA)
    @JvmField val NASTRAN_STATEMENT = createTextAttributesKey("NASTRAN_STATEMENT", Default.PREDEFINED_SYMBOL)
    @JvmField val FMS_KEYWORD = createTextAttributesKey("NASTRAN_FMS_KEYWORD", Default.STATIC_METHOD)
    @JvmField val EXEC_KEYWORD = createTextAttributesKey("NASTRAN_EXEC_KEYWORD", Default.FUNCTION_CALL)
    @JvmField val CASE_KEYWORD = createTextAttributesKey("NASTRAN_CASE_KEYWORD", Default.FUNCTION_DECLARATION)
    @JvmField val BULK_CARD = createTextAttributesKey("NASTRAN_BULK_CARD", Default.KEYWORD)
    @JvmField val PARAM_NAME = createTextAttributesKey("NASTRAN_PARAM_NAME", Default.CONSTANT)
    @JvmField val STRING = createTextAttributesKey("NASTRAN_STRING", Default.STRING)
    @JvmField val TEXT = createTextAttributesKey("NASTRAN_TEXT", HighlighterColors.TEXT)
    /** Background of every other bulk data field; defaults are in colorSchemes/. */
    @JvmField val FIELD_ALT = createTextAttributesKey("NASTRAN_FIELD_ALT")
    @JvmField val FILE_LINK = createTextAttributesKey("NASTRAN_FILE_LINK", CodeInsightColors.HYPERLINK_ATTRIBUTES)
}

class NastranSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = NastranLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        pack(KEYS[tokenType])

    private companion object {
        val KEYS: Map<IElementType, TextAttributesKey> = mapOf(
            NastranTokenTypes.COMMENT to NastranColors.COMMENT,
            NastranTokenTypes.SECTION_MARKER to NastranColors.SECTION_MARKER,
            NastranTokenTypes.NASTRAN_STATEMENT to NastranColors.NASTRAN_STATEMENT,
            NastranTokenTypes.FMS_KEYWORD to NastranColors.FMS_KEYWORD,
            NastranTokenTypes.EXEC_KEYWORD to NastranColors.EXEC_KEYWORD,
            NastranTokenTypes.CASE_KEYWORD to NastranColors.CASE_KEYWORD,
            NastranTokenTypes.BULK_CARD to NastranColors.BULK_CARD,
            NastranTokenTypes.PARAM_NAME to NastranColors.PARAM_NAME,
            NastranTokenTypes.STRING to NastranColors.STRING,
            NastranTokenTypes.TEXT to NastranColors.TEXT,
        )
    }
}

class NastranSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        NastranSyntaxHighlighter()
}
