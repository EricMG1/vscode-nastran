package com.nastran.jetbrains.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.nastran.jetbrains.core.CardDetector
import com.nastran.jetbrains.core.DocsHtml
import com.nastran.jetbrains.core.NastranDocs
import com.nastran.jetbrains.includes.NastranIncludeService
import com.nastran.jetbrains.lang.NastranLanguage

/**
 * Shows the MSC Nastran quick reference for the entry under the caret or
 * mouse (Quick Documentation and hover).
 */
class NastranDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? = contextElement?.takeIf { file.language == NastranLanguage && documentation(it) != null }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? =
        (element ?: originalElement)?.let(::documentation)

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? =
        generateDoc(element, originalElement)

    private fun documentation(element: PsiElement): String? {
        if (element.language != NastranLanguage) return null
        val file = element.containingFile ?: return null
        val virtualFile = file.originalFile.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(element.project).getDocument(file) ?: return null
        val offset = element.textRange.startOffset
        val line = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(line)
        val text = document.getText(TextRange(lineStart, document.getLineEndOffset(line)))
        val section = NastranIncludeService.getInstance(element.project).sectionAt(virtualFile, document, line)
        val card = CardDetector.cardAt(text, section, offset - lineStart) ?: return null
        val markdown = NastranDocs.bundled.docs(card.name, section, text)
        return if (markdown.isEmpty()) null else DocsHtml.toHtml(markdown)
    }
}
