package com.nastran.jetbrains.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/** Folds Case Control (CEND to BEGIN BULK) and Bulk Data (BEGIN BULK to ENDDATA). */
class NastranFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        var cend = -1
        var beginBulk = -1
        var endData = -1
        for (line in 0 until document.lineCount) {
            val start = document.getLineStartOffset(line)
            val text = document.charsSequence.subSequence(start, minOf(start + 12, document.getLineEndOffset(line))).toString().uppercase()
            when {
                cend < 0 && text.startsWith("CEND") -> cend = line
                beginBulk < 0 && BEGIN_BULK.containsMatchIn(text) -> beginBulk = line
                beginBulk >= 0 && text.startsWith("ENDDATA") -> { endData = line; break }
            }
        }
        val regions = ArrayList<FoldingDescriptor>()
        fun fold(fromLine: Int, toLine: Int) {
            if (fromLine < 0 || toLine <= fromLine + 1) return
            val range = TextRange(document.getLineEndOffset(fromLine), document.getLineEndOffset(toLine - 1))
            if (!range.isEmpty) regions.add(FoldingDescriptor(root.node, range))
        }
        if (cend >= 0 && beginBulk > cend) fold(cend, beginBulk)
        if (beginBulk >= 0) fold(beginBulk, if (endData >= 0) endData else document.lineCount)
        return regions.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = " ..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    private companion object {
        val BEGIN_BULK = Regex("^BEGIN\\s+BULK")
    }
}
