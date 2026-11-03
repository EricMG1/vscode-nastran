package com.nastran.jetbrains.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.nastran.jetbrains.includes.NastranIncludeService
import com.nastran.jetbrains.lang.NastranFileType
import com.nastran.jetbrains.settings.NastranSettings

/** The Nastran file an action applies to: the event's file, else the selected editor's. */
internal fun nastranFile(e: AnActionEvent): VirtualFile? {
    e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.fileType == NastranFileType }?.let { return it }
    val project = e.project ?: return null
    return FileEditorManager.getInstance(project).selectedFiles.firstOrNull { it.fileType == NastranFileType }
}

/** The "Nastran" submenu, shown only for Nastran files. */
class NastranActionGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType == NastranFileType
    }
}

/** Rebuilds the include hierarchy with the current Nastran file as root deck. */
class RebuildIncludeTreeAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            (nastranFile(e) != null || NastranIncludeService.getInstance(project).rootFile != null)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = NastranIncludeService.getInstance(project)
        val file = nastranFile(e)
        if (file != null) service.rebuild(file) else service.refresh()
    }
}

/** Inserts a field ruler comment above the caret line. */
abstract class InsertRulerAction : AnAction(), DumbAware {
    protected abstract fun ruler(settings: NastranSettings): String

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null &&
            e.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType == NastranFileType
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document
        val offset = document.getLineStartOffset(document.getLineNumber(editor.caretModel.offset))
        val text = ruler(NastranSettings.getInstance()) + "\n"
        WriteCommandAction.runWriteCommandAction(project, "Insert Field Ruler", null, {
            document.insertString(offset, text)
        })
    }
}

class InsertShortRulerAction : InsertRulerAction() {
    override fun ruler(settings: NastranSettings) = settings.shortRuler
}

class InsertLongRulerAction : InsertRulerAction() {
    override fun ruler(settings: NastranSettings) = settings.longRuler
}
