package com.nastran.jetbrains.lang

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class NastranTokenType(debugName: String) : IElementType(debugName, NastranLanguage)

object NastranTokenTypes {
    @JvmField val COMMENT = NastranTokenType("COMMENT")
    /** CEND, BEGIN BULK, ENDDATA and the other delimiters. */
    @JvmField val SECTION_MARKER = NastranTokenType("SECTION_MARKER")
    @JvmField val NASTRAN_STATEMENT = NastranTokenType("NASTRAN_STATEMENT")
    @JvmField val FMS_KEYWORD = NastranTokenType("FMS_KEYWORD")
    @JvmField val EXEC_KEYWORD = NastranTokenType("EXEC_KEYWORD")
    @JvmField val CASE_KEYWORD = NastranTokenType("CASE_KEYWORD")
    @JvmField val BULK_CARD = NastranTokenType("BULK_CARD")
    @JvmField val PARAM_NAME = NastranTokenType("PARAM_NAME")
    @JvmField val STRING = NastranTokenType("STRING")
    @JvmField val TEXT = NastranTokenType("TEXT")
    @JvmField val EOL = NastranTokenType("EOL")
    @JvmField val WHITESPACE: IElementType = TokenType.WHITE_SPACE

    @JvmField val KEYWORDS = TokenSet.create(
        SECTION_MARKER, NASTRAN_STATEMENT, FMS_KEYWORD, EXEC_KEYWORD, CASE_KEYWORD, BULK_CARD, PARAM_NAME,
    )
}
