package net.plpgsql.ideadebugger.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.treeStructure.Tree
import net.plpgsql.ideadebugger.action.openPopup
import net.plpgsql.ideadebugger.service.DebuggerEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ExplorerPanel(val project: Project): SimpleToolWindowPanel(true, false) {

    private val filesNode = FilesTreeNode()

    private val root = DefaultMutableTreeNode().apply {
        add(filesNode)
    }

    private val treeModel = DefaultTreeModel(root)
    val jbTree = Tree(treeModel)

    init {
        jbTree.isRootVisible = false
        //Cell renderer
        jbTree.cellRenderer = DebuggerItemRenderer()
        //React to double click on node
        jbTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                //Right click
                if (e.button == MouseEvent.BUTTON3){
                    //Perform an action
                    nodeFromMouseEvent(e)?.let {
                        (it.userObject as? ItemWrapperForTree)?.let { node ->
                            openPopup(project, e, node.delegate())
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
//            is SourceFile -> actionFind(OpenSourceFileAction::class.java)?.let { action ->
//                dataContextForNode(project, objectNode).let { context ->
//                    AnActionEvent.createFromDataContext("", null, context).let { event ->
//                        action.actionPerformed(event)
//                    }
//                }
//            }
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
//        runInEdt {
//            when (event) {
//                is SourceLoaded -> addFile(event.file)
//                is ProcessCreated -> addProcess(event.processInfo)
//                is ProcessFinished -> removeProcess(event.processInfo)
////                is ProcessStateChanged -> replaceDebugNode(event.state)
//                else -> {}
//            }
//        }
    }

//    private fun addFile(file: SourceFile) {
//        treeModel.insertNodeInto(DefaultMutableTreeNode(file), fileNode, 0)
//        treeModel.reload()
//    }
//
//    private fun addProcess(processInfo: ProcessInfo) {
//        treeModel.insertNodeInto(DefaultMutableTreeNode(processInfo), debugNode, debugNode.childCount)
//        treeModel.reload()
//    }
//
//    private fun removeProcess(processInfo: ProcessInfo) {
//        val node = debugNode.children().toList().map { it as DefaultMutableTreeNode }
//            .firstOrNull { it.userObject == processInfo
//        }
//        if (node != null) {
//            treeModel.removeNodeFromParent(node)
//        }
//        treeModel.reload()
//    }
//
//    private fun replaceDebugNode(state: ProcessState) {
//        removeProcess(state.processInfo)
//        treeModel.insertNodeInto(DefaultMutableTreeNode(state), debugNode, debugNode.childCount)
//        treeModel.reload()
//    }
}
