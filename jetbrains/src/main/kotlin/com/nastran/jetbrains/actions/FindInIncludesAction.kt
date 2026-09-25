package com.nastran.jetbrains.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.nastran.jetbrains.includes.NastranIncludeService

/**
 * Finds every occurrence of the selected text (or text typed in a prompt)
 * across the root deck and all of its include files, and lists them in the
 * Find tool window. Port of client/src/find.ts.
 */
class FindInIncludesAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && nastranFile(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = nastranFile(e) ?: return
        val selected = e.getData(CommonDataKeys.EDITOR)?.selectionModel?.selectedText
        val query = selected?.takeIf { it.isNotEmpty() }
            ?: Messages.showInputDialog(project, "Text to search the include hierarchy for:", "Find in Includes", null)
            ?: return
        if (query.isEmpty()) return
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Searching include files", true) {
            private var usages: List<Usage> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                val service = NastranIncludeService.getInstance(project)
                val tree = service.tree?.takeIf { file.path in it } ?: service.rebuildNow(file)
                usages = tree.files.flatMap { path ->
                    indicator.checkCanceled()
                    ReadAction.compute<List<Usage>, RuntimeException> { find(project, path, query) }
                }
            }

            override fun onSuccess() {
                if (usages.isEmpty()) {
                    Messages.showInfoMessage(project, "'$query' was not found in the include hierarchy.", "Find in Includes")
                    return
                }
                val presentation = UsageViewPresentation().apply {
                    tabText = "'$query' in includes"
                    toolwindowTitle = "Find in Includes"
                    searchString = query
                    isOpenInNewTab = true
                }
                UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, usages.toTypedArray(), presentation)
            }
        })
    }

    private fun find(project: Project, path: String, query: String): List<Usage> {
        val virtualFile = NastranIncludeService.getInstance(project).findFile(path) ?: return emptyList()
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return emptyList()
        val text = psiFile.viewProvider.contents
        val result = ArrayList<Usage>()
        var index = text.indexOf(query)
        while (index >= 0) {
            result.add(UsageInfo2UsageAdapter(UsageInfo(psiFile, index, index + query.length)))
            index = text.indexOf(query, index + query.length)
        }
        return result
    }
}
