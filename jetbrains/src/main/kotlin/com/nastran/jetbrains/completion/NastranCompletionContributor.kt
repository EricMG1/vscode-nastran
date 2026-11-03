package com.nastran.jetbrains.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.nastran.jetbrains.core.NastranDocs
import com.nastran.jetbrains.core.Section
import com.nastran.jetbrains.includes.NastranIncludeService
import com.nastran.jetbrains.lang.NastranIcons
import com.nastran.jetbrains.lang.NastranLanguage

/**
 * Completes entry names at the start of a line for the section the line is
 * in. Bulk data cards insert their field template, as in VS Code, and the
 * second field of a PARAM entry completes parameter names.
 */
class NastranCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(NastranLanguage), Provider())
    }

    private class Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val document = parameters.editor.document
            val offset = parameters.offset
            val line = document.getLineNumber(offset)
            val before = document.getText(TextRange(document.getLineStartOffset(line), offset))
            if (before.trimStart().startsWith("$")) return
            val file = parameters.originalFile.virtualFile ?: return
            val section = NastranIncludeService.getInstance(parameters.position.project).sectionAt(file, document, line)
            val docs = NastranDocs.bundled

            val param = PARAM_FIELD.find(before)
            if (section == Section.BULK && param != null) {
                val names = result.withPrefixMatcher(param.groupValues[1]).caseInsensitive()
                for (name in docs.paramNames()) {
                    names.addElement(LookupElementBuilder.create(name).withTypeText("PARAM").withIcon(NastranIcons.FILE))
                }
                result.stopHere()
                return
            }

            // Otherwise only the first word of a line is completed
            val word = before.trimStart()
            if (word.any { it.isWhitespace() || it in ",=(" }) return
            val names = result.withPrefixMatcher(word).caseInsensitive()
            for (name in docs.cardNames(section)) {
                var lookup = LookupElementBuilder.create(name).withTypeText(section.name).withIcon(NastranIcons.FILE)
                if (section == Section.BULK) {
                    lookup = lookup.withInsertHandler { ctx, item ->
                        val template = NastranDocs.completionTemplate(docs.docs(item.lookupString, Section.BULK, item.lookupString))
                            .trimEnd()
                        if (template.isNotEmpty()) {
                            ctx.document.replaceString(ctx.startOffset, ctx.tailOffset, template)
                            ctx.editor.caretModel.moveToOffset(ctx.startOffset + template.length)
                        }
                    }
                }
                names.addElement(lookup)
            }
            result.stopHere()
        }
    }

    private companion object {
        /** The caret is in the second field of a PARAM entry. */
        val PARAM_FIELD = Regex("""^PARAM\*?(?:\s+|\s*,\s*)([A-Za-z0-9_]*)$""", RegexOption.IGNORE_CASE)
    }
}
