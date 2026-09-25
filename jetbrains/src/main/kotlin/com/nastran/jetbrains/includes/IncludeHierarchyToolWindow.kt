package com.nastran.jetbrains.includes

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.nastran.jetbrains.core.IncludeTree
import com.nastran.jetbrains.lang.NastranIcons
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class IncludeHierarchyToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = IncludeHierarchyPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

/** Tree of the root deck and the files it includes. */
class IncludeHierarchyPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {
    private val model = DefaultTreeModel(DefaultMutableTreeNode("Open a Nastran file to see its includes"))
    private val tree = Tree(model)

    init {
        tree.cellRenderer = Renderer()
        setContent(ScrollPaneFactory.createScrollPane(tree))

        val actions = DefaultActionGroup(ActionManager.getInstance().getAction("Nastran.RebuildIncludeTree"))
        val toolbar = ActionManager.getInstance().createActionToolbar("NastranIncludeHierarchy", actions, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = openSelected()
        }.installOn(tree)
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && openSelected()) e.consume()
            }
        })

        project.messageBus.connect(this).subscribe(NastranIncludeListener.TOPIC, NastranIncludeListener { show(it) })

        val service = NastranIncludeService.getInstance(project)
        val current = service.tree
        if (current != null) {
            show(current)
        } else {
            FileEditorManager.getInstance(project).selectedFiles.firstOrNull { it.isNastran() }?.let(service::rebuild)
        }
    }

    private fun show(includeTree: IncludeTree) {
        model.setRoot(toNode(includeTree.root))
        TreeUtil.expandAll(tree)
    }

    private fun toNode(node: IncludeTree.Node): DefaultMutableTreeNode =
        DefaultMutableTreeNode(node).apply { node.children.forEach { add(toNode(it)) } }

    private fun openSelected(): Boolean {
        val node = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? IncludeTree.Node ?: return false
        if (!node.exists) return false
        val file = NastranIncludeService.getInstance(project).findFile(node.path) ?: return false
        OpenFileDescriptor(project, file).navigate(true)
        return true
    }

    override fun dispose() = Unit

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean,
        ) {
            val node = (value as? DefaultMutableTreeNode)?.userObject
            if (node !is IncludeTree.Node) {
                append(node?.toString() ?: "", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                return
            }
            icon = if (node.exists) NastranIcons.INCLUDE else NastranIcons.INCLUDE_MISSING
            append(node.label, if (node.exists) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.ERROR_ATTRIBUTES)
            if (!node.exists) append("  not found", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }
}
