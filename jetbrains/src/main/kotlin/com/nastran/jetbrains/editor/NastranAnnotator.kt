package com.nastran.jetbrains.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.nastran.jetbrains.core.FieldSplitter
import com.nastran.jetbrains.core.Section
import com.nastran.jetbrains.includes.NastranIncludeService
import com.nastran.jetbrains.lang.NastranColors
import com.nastran.jetbrains.psi.NastranElementTypes
import com.nastran.jetbrains.psi.NastranIncludePath

/**
 * Shades every other bulk data field, marks `file://` links in comments and
 * flags INCLUDE statements whose file cannot be found.
 */
class NastranAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when {
            element.node.elementType == NastranElementTypes.ENTRY -> shadeFields(element, holder)
            element is NastranIncludePath -> checkInclude(element, holder)
            element is PsiComment -> markLinks(element, holder)
        }
    }

    private fun shadeFields(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile
        val virtualFile = file.originalFile.virtualFile ?: return
        val document = PsiDocumentManager.getInstance(element.project).getDocument(file) ?: return
        val line = document.getLineNumber(element.textRange.startOffset)
        val section = NastranIncludeService.getInstance(element.project).sectionAt(virtualFile, document, line)
        if (section != Section.BULK) return
        val lineStart = document.getLineStartOffset(line)
        val ranges = FieldSplitter.shadedFields(line) { i ->
            if (i < 0 || i >= document.lineCount) null
            else document.getText(TextRange(document.getLineStartOffset(i), document.getLineEndOffset(i)))
        }
        val bounds = element.textRange
        for (range in ranges) {
            val shaded = TextRange(lineStart + range.first, lineStart + range.last + 1).intersection(bounds) ?: continue
            if (shaded.isEmpty) continue
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(shaded)
                .textAttributes(NastranColors.FIELD_ALT)
                .create()
        }
    }

    private fun checkInclude(element: NastranIncludePath, holder: AnnotationHolder) {
        if (element.reference.resolve() != null) return
        holder.newAnnotation(HighlightSeverity.WARNING, "Include file not found")
            .range(element)
            .create()
    }

    private fun markLinks(comment: PsiComment, holder: AnnotationHolder) {
        val text = comment.text
        var index = text.indexOf(FILE_SCHEME)
        while (index >= 0) {
            var end = index
            while (end < text.length && !text[end].isWhitespace()) end++
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(index, end).shiftRight(comment.textRange.startOffset))
                .textAttributes(NastranColors.FILE_LINK)
                .create()
            index = text.indexOf(FILE_SCHEME, end)
        }
    }

    companion object {
        const val FILE_SCHEME = "file://"
    }
}
