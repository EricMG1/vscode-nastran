package com.nastran.jetbrains.includes

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.messages.Topic
import com.nastran.jetbrains.core.IncludeParser
import com.nastran.jetbrains.core.IncludeTree
import com.nastran.jetbrains.core.Section
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Notified on the EDT whenever the include tree is rebuilt. */
fun interface NastranIncludeListener {
    fun treeChanged(tree: IncludeTree)

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Nastran include tree", NastranIncludeListener::class.java)
    }
}

/**
 * Holds the include hierarchy of the current root deck, which tells every
 * other feature which section (FMS, Executive, Case Control or Bulk Data)
 * a line of an include file belongs to.
 */
@Service(Service.Level.PROJECT)
class NastranIncludeService(private val project: Project) : Disposable {
    @Volatile
    var tree: IncludeTree? = null
        private set

    @Volatile
    private var root: VirtualFile? = null

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    /** Sections for files outside the tree, keyed by path with the document stamp. */
    private val standalone = ConcurrentHashMap<String, Pair<Long, IncludeTree>>()

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (tree?.contains(file.path) == true) schedule(REBUILD_DELAY_MS)
            }
        }, this)
    }

    val rootFile: VirtualFile? get() = root?.takeIf { it.isValid }

    /** Makes [file] the root deck and rebuilds the tree in the background. */
    fun rebuild(file: VirtualFile) {
        root = file
        schedule(0)
    }

    /** Rebuilds the tree with the current root deck. */
    fun refresh() {
        if (root != null) schedule(0)
    }

    /** Makes [file] the root deck unless it is already part of the tree. */
    fun ensureContains(file: VirtualFile) {
        if (tree?.contains(file.path) != true && root != file) rebuild(file)
    }

    /** Builds the tree for [file] on the calling thread; call from a background task. */
    fun rebuildNow(file: VirtualFile): IncludeTree {
        root = file
        alarm.cancelAllRequests()
        return build(file)
    }

    /** A file of the include tree by path, on the root deck's file system. */
    fun findFile(path: String): VirtualFile? =
        (root?.fileSystem ?: LocalFileSystem.getInstance()).findFileByPath(path)?.takeIf { !it.isDirectory }

    fun sectionAt(file: VirtualFile, document: Document, line: Int): Section {
        val text = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
        val current = tree
        if (current != null && file.path in current) return current.sectionAt(file.path, line, text)
        // Not part of the include tree: use the file's own CEND and BEGIN BULK
        val stamp = document.modificationStamp
        val cached = standalone[file.path]
        val own = if (cached != null && cached.first == stamp) {
            cached.second
        } else {
            IncludeTree.build(file.path, { if (it == file.path) document.text.lines() else null }, { false })
                .also { standalone[file.path] = stamp to it }
        }
        return own.sectionAt(file.path, line, text)
    }

    /** The file an INCLUDE path in [from] refers to, or null if it does not exist. */
    fun resolveInclude(path: String, from: VirtualFile): VirtualFile? {
        val rootPath = if (tree?.contains(from.path) == true) root?.path else from.path
        return IncludeParser.candidates(path, from.path, rootPath)
            .firstNotNullOfOrNull { candidate -> from.fileSystem.findFileByPath(candidate)?.takeIf { !it.isDirectory } }
    }

    private fun schedule(delayMs: Int) {
        alarm.cancelAllRequests()
        alarm.addRequest({ root?.let { build(it) } }, delayMs)
    }

    private fun build(file: VirtualFile): IncludeTree {
        val built = ReadAction.compute<IncludeTree, RuntimeException> {
            IncludeTree.build(file.path, ::readLines) { findFile(it) != null }
        }
        tree = built
        standalone.clear()
        ApplicationManager.getApplication().invokeLater({
            project.messageBus.syncPublisher(NastranIncludeListener.TOPIC).treeChanged(built)
            DaemonCodeAnalyzer.getInstance(project).restart()
        }, project.disposed)
        return built
    }

    private fun readLines(path: String): List<String>? {
        val file = findFile(path) ?: return null
        FileDocumentManager.getInstance().getCachedDocument(file)?.let { return it.text.lines() }
        return try {
            VfsUtilCore.loadText(file).lines()
        } catch (_: IOException) {
            null
        }
    }

    override fun dispose() = Unit

    companion object {
        private const val REBUILD_DELAY_MS = 700

        fun getInstance(project: Project): NastranIncludeService = project.service()
    }
}
