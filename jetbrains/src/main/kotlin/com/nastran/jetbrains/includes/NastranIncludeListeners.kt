package com.nastran.jetbrains.includes

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.nastran.jetbrains.lang.NastranFileType

internal fun VirtualFile.isNastran(): Boolean = fileType == NastranFileType

/**
 * Switching to a Nastran file that is not part of the current include tree
 * makes it the new root deck, as the VS Code extension does.
 */
class NastranEditorListener(private val project: Project) : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        if (file.isNastran()) NastranIncludeService.getInstance(project).ensureContains(file)
    }
}

/** Builds the tree for a Nastran file that is already open when the project opens. */
class NastranStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull { it.isNastran() } ?: return
        NastranIncludeService.getInstance(project).ensureContains(file)
    }
}
