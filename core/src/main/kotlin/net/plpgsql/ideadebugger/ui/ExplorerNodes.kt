package net.plpgsql.ideadebugger.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import net.plpgsql.ideadebugger.*
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

sealed interface ItemWrapperForTree {
    fun delegate(): DebuggerItem
    fun display(): String
    fun icon(): Icon?
}


data class FilesFolderWrapper(val folder: FilesRoot) : ItemWrapperForTree {
    override fun delegate(): DebuggerItem = folder

    override fun display(): String = folder.title

    override fun icon(): Icon = AllIcons.Nodes.Folder
}

data class ProcessWrapper(val process: FileItem) : ItemWrapperForTree {

    override fun delegate(): DebuggerItem = process
    override fun display(): String = "name"

    override fun icon(): Icon = AllIcons.Debugger.ThreadRunning
}

data class SourceFileWrapper(val fileItem: FileItem) : ItemWrapperForTree {

    override fun delegate(): DebuggerItem = fileItem
    override fun display(): String = "name"

    override fun icon(): Icon = AllIcons.Nodes.Function
}

abstract class WrapperTreeNode(val item: ItemWrapperForTree) : DefaultMutableTreeNode(item)

class FilesTreeNode : WrapperTreeNode(FilesFolderWrapper(FilesRoot("FileSystem")))

//class ProcessTreeNode(process: FilesTreeNode) : WrapperTreeNode(ProcessWrapper(null))

class SourceFileTreeNode(fileItem: FileItem) : WrapperTreeNode(SourceFileWrapper(fileItem))

class DebuggerItemRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        if (value is WrapperTreeNode) {
            icon = value.item.icon()
            append(value.item.display())
        }
    }
}