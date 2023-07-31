package net.plpgsql.ideadebugger.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.treeStructure.Tree
import net.plpgsql.ideadebugger.action.OpenSourceFileAction
import net.plpgsql.ideadebugger.action.actionFind
import net.plpgsql.ideadebugger.action.dataContextForNode
import net.plpgsql.ideadebugger.action.openPopup
import net.plpgsql.ideadebugger.node.*
import net.plpgsql.ideadebugger.service.DebuggerEvent
import net.plpgsql.ideadebugger.service.ProcessCreated
import net.plpgsql.ideadebugger.service.ProcessFinished
import net.plpgsql.ideadebugger.service.SourceLoaded
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ExplorerPanel(val project: Project): SimpleToolWindowPanel(true, false) {

    private val fileNode = DefaultMutableTreeNode(FilesNode())

    private val debugNode = DefaultMutableTreeNode(ProcessesNode())

    private val dbNode = DefaultMutableTreeNode("Database")

    private val root = DefaultMutableTreeNode().apply {
        add(fileNode)
        add(debugNode)
        add(dbNode)
    }

    private val treeModel = DefaultTreeModel(root)
    val jbTree = Tree(treeModel)

    init {
        jbTree.isRootVisible = false
        //Cell renderer
        jbTree.cellRenderer = object : ColoredTreeCellRenderer() {

            override fun customizeCellRenderer(
                tree: JTree,
                value: Any,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                if (value is DefaultMutableTreeNode) {
                    icon = getIcon(value)
                    append(getText(value))
                }
            }

            private fun getIcon(treeNode: DefaultMutableTreeNode): Icon? {
                return when (val objectNode = treeNode.userObject) {
                    is FolderNode -> when (objectNode) {
                        is FilesNode -> AllIcons.Nodes.Folder
                        is ProcessesNode -> AllIcons.Nodes.Folder
                    }
                    is SourceFile -> AllIcons.Nodes.Function
                    is ProcessInfo -> AllIcons.Debugger.ThreadRunning
                    else -> null
                }

            }

            private fun getText(treeNode: DefaultMutableTreeNode): String {
                return when (val objectNode = treeNode.userObject) {
                    is FolderNode -> when (objectNode) {
                        is FilesNode -> "Files"
                        is ProcessesNode -> "Processes"
                    }
                    is SourceFile -> "${objectNode.name} (${objectNode.path})"
                    is ProcessInfo -> "${objectNode.id} [${objectNode.mode}]"
                    else -> "Unknown"
                }
            }
        }
        //React to double click on node
        jbTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                //Right click
                if (e.button == MouseEvent.BUTTON3){
                    //Perform an action
                    nodeFromMouseEvent(e)?.let {
                        (it.userObject as? DebuggerNode)?.let { node ->
                            openPopup(project, node, e)
                        }
                    }
                } else if (e.clickCount == 2) {
                    // Single click
                    nodeFromMouseEvent(e)?.let { node ->
                        handleDoubleClick(e, node)
                    }
                }
            }
        })

        setContent(jbTree)
    }

    private fun handleDoubleClick(e: MouseEvent, node: DefaultMutableTreeNode) {
        when (val objectNode = node.userObject) {
            is SourceFile -> actionFind(OpenSourceFileAction::class.java)?.let { action ->
                dataContextForNode(project, objectNode).let { context ->
                    AnActionEvent.createFromDataContext("", null, context).let { event ->

                        action.actionPerformed(event)
                    }
                }
            }
        }
    }

    fun nodeFromMouseEvent(e: MouseEvent): DefaultMutableTreeNode? {
        val selRow = jbTree.getRowForLocation(e.x, e.y)
        val selPath = jbTree.getPathForLocation(e.x, e.y)
        if (selRow != -1) {
            // Single click
            val lastPathComponent = selPath?.lastPathComponent
            if (lastPathComponent is DefaultMutableTreeNode) {
                return lastPathComponent
            }
        }
        return null
    }

    fun handleEvent(event: DebuggerEvent) {
        runInEdt {
            when (event) {
                is SourceLoaded -> addFile(event.file)
                is ProcessCreated -> addProcess(event.processInfo)
                is ProcessFinished -> removeProcess(event.processInfo)
//                is ProcessStateChanged -> replaceDebugNode(event.state)
                else -> {}
            }
        }
    }

    private fun addFile(file: SourceFile) {
        treeModel.insertNodeInto(DefaultMutableTreeNode(file), fileNode, 0)
        treeModel.reload()
    }

    private fun addProcess(processInfo: ProcessInfo) {
        treeModel.insertNodeInto(DefaultMutableTreeNode(processInfo), debugNode, debugNode.childCount)
        treeModel.reload()
    }

    private fun removeProcess(processInfo: ProcessInfo) {
        val node = debugNode.children().toList().map { it as DefaultMutableTreeNode }
            .firstOrNull { it.userObject == processInfo
        }
        if (node != null) {
            treeModel.removeNodeFromParent(node)
        }
        treeModel.reload()
    }
//
//    private fun replaceDebugNode(state: ProcessState) {
//        removeProcess(state.processInfo)
//        treeModel.insertNodeInto(DefaultMutableTreeNode(state), debugNode, debugNode.childCount)
//        treeModel.reload()
//    }
}