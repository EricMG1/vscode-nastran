package com.nastran.jetbrains.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.nastran.jetbrains.core.IncludeParser
import com.nastran.jetbrains.lang.NastranFileType
import com.nastran.jetbrains.lang.NastranLanguage
import com.nastran.jetbrains.lang.NastranLexer
import com.nastran.jetbrains.lang.NastranTokenTypes

class NastranElementType(debugName: String) : IElementType(debugName, NastranLanguage)

object NastranElementTypes {
    @JvmField val FILE = IFileElementType(NastranLanguage)
    /** One line of the deck (a card, command or statement). */
    @JvmField val ENTRY = NastranElementType("ENTRY")
    @JvmField val INCLUDE_STATEMENT = NastranElementType("INCLUDE_STATEMENT")
    @JvmField val INCLUDE_PATH = NastranElementType("INCLUDE_PATH")
}

class NastranFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NastranLanguage) {
    override fun getFileType(): FileType = NastranFileType

    override fun toString(): String = "Nastran file"
}

/** The quoted path of an INCLUDE statement; Ctrl+Click opens the file. */
class NastranIncludePath(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference = NastranIncludeReference(this)

    override fun getReferences(): Array<PsiReference> = arrayOf(reference)
}

class NastranParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = NastranLexer()

    override fun createParser(project: Project?): PsiParser = NastranParser()

    override fun getFileNodeType(): IFileElementType = NastranElementTypes.FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    override fun getCommentTokens(): TokenSet = TokenSet.create(NastranTokenTypes.COMMENT)

    override fun getStringLiteralElements(): TokenSet = TokenSet.create(NastranTokenTypes.STRING)

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        NastranElementTypes.INCLUDE_PATH -> NastranIncludePath(node)
        else -> ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = NastranFile(viewProvider)
}

/**
 * Groups tokens into one element per line. INCLUDE statements (which must
 * start in column 1) get their own element, holding the quoted path.
 */
class NastranParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val file = builder.mark()
        while (!builder.eof()) {
            if (builder.tokenType == NastranTokenTypes.EOL) {
                builder.advanceLexer()
                continue
            }
            val atColumnOne = builder.currentOffset.let { it == 0 || builder.originalText[it - 1] == '\n' }
            if (atColumnOne && IncludeParser.isIncludeLine(builder.tokenText ?: "")) {
                parseInclude(builder)
            } else {
                val entry = builder.mark()
                while (!builder.eof() && builder.tokenType != NastranTokenTypes.EOL) builder.advanceLexer()
                entry.done(NastranElementTypes.ENTRY)
            }
        }
        file.done(root)
        return builder.treeBuilt
    }

    private fun parseInclude(builder: PsiBuilder) {
        val statement = builder.mark()
        var continued = false
        var lines = 0
        while (!builder.eof()) {
            if (builder.tokenType == NastranTokenTypes.EOL) {
                // An unterminated path continues on the next line
                if (!continued || lines > 0) break
                lines++
                builder.advanceLexer()
                continue
            }
            if (builder.tokenType == NastranTokenTypes.STRING && lines == 0) {
                val text = builder.tokenText ?: ""
                continued = text.length < 2 || text.last() != text.first()
                val path = builder.mark()
                builder.advanceLexer()
                path.done(NastranElementTypes.INCLUDE_PATH)
                continue
            }
            builder.advanceLexer()
        }
        statement.done(NastranElementTypes.INCLUDE_STATEMENT)
    }
}
