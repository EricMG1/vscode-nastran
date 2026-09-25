package com.nastran.jetbrains.editor

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.nastran.jetbrains.lang.NastranLanguage

/**
 * Makes `file:///path#line` links in comments navigable. The VS Code
 * extension writes these links in its "find" results.
 */
class FileLinkReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiComment().withLanguage(NastranLanguage),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val text = element.text
                    val refs = ArrayList<PsiReference>()
                    var index = text.indexOf(NastranAnnotator.FILE_SCHEME)
                    while (index >= 0) {
                        var end = index
                        while (end < text.length && !text[end].isWhitespace()) end++
                        refs.add(FileLinkReference(element as PsiComment, TextRange(index, end)))
                        index = text.indexOf(NastranAnnotator.FILE_SCHEME, end)
                    }
                    return refs.toTypedArray()
                }
            },
        )
    }
}

private class FileLinkReference(comment: PsiComment, range: TextRange) :
    PsiReferenceBase<PsiComment>(comment, range, true) {

    override fun resolve(): PsiElement? {
        val link = rangeInElement.substring(element.text).removePrefix(NastranAnnotator.FILE_SCHEME)
        // VS Code writes "file:///" + the absolute path, so there may be extra slashes
        val bare = link.substringBefore('#').trimStart('/').replace('\\', '/')
        val path = if (Regex("^[A-Za-z]:").containsMatchIn(bare)) bare else "/$bare"
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(file) ?: return null
        val line = link.substringAfter('#', "").toIntOrNull() ?: return psiFile
        val document = PsiDocumentManager.getInstance(element.project).getDocument(psiFile) ?: return psiFile
        if (line < 1 || line > document.lineCount) return psiFile
        return psiFile.findElementAt(document.getLineStartOffset(line - 1)) ?: psiFile
    }

    override fun handleElementRename(newElementName: String): PsiElement = element

    override fun bindToElement(element: PsiElement): PsiElement = this.element
}
