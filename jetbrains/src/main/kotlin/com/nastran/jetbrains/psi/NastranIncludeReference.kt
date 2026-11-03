package com.nastran.jetbrains.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.nastran.jetbrains.core.IncludeParser
import com.nastran.jetbrains.includes.NastranIncludeService

/** Resolves an INCLUDE path to the file it names. */
class NastranIncludeReference(element: NastranIncludePath) :
    PsiReferenceBase<NastranIncludePath>(element, pathRange(element), true) {

    override fun resolve(): PsiElement? {
        val file = element.containingFile ?: return null
        val virtualFile = file.originalFile.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(element.project).getDocument(file) ?: return null
        val line = document.getLineNumber(element.textRange.startOffset)
        val lines = (line..minOf(line + 1, document.lineCount - 1)).map {
            document.getText(TextRange(document.getLineStartOffset(it), document.getLineEndOffset(it)))
        }
        val include = IncludeParser.parse(lines, 0) ?: return null
        val target = NastranIncludeService.getInstance(element.project).resolveInclude(include.path, virtualFile) ?: return null
        return PsiManager.getInstance(element.project).findFile(target)
    }

    override fun isReferenceTo(element: PsiElement): Boolean =
        element is PsiFileSystemItem && element.manager.areElementsEquivalent(resolve(), element)

    // Renaming or moving an included file does not rewrite INCLUDE statements
    override fun handleElementRename(newElementName: String): PsiElement = element

    override fun bindToElement(element: PsiElement): PsiElement = this.element

    private companion object {
        fun pathRange(element: PsiElement): TextRange {
            val text = element.text
            if (text.isEmpty()) return TextRange.EMPTY_RANGE
            val end = if (text.length > 1 && text.last() == text.first()) text.length - 1 else text.length
            return TextRange(1, maxOf(1, end))
        }
    }
}
