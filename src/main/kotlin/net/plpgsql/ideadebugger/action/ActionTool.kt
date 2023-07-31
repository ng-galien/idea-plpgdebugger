package net.plpgsql.ideadebugger.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.node.DebuggerNode
import net.plpgsql.ideadebugger.node.FolderNode
import net.plpgsql.ideadebugger.node.ProcessInfo
import net.plpgsql.ideadebugger.node.SourceFile
import java.awt.event.MouseEvent


val SOURCE_FILE_KEY: DataKey<SourceFile> = DataKey.create("sourceFile")

val PROCESS_INFO_KEY: DataKey<ProcessInfo> = DataKey.create("processInfo")

val DEBUGGER_NODE_KEY: DataKey<DebuggerNode> = DataKey.create("debuggerNode")

//Get a Generic Action instance with the given class name which is the id of the action
 fun <T> actionFind(clazz: Class<T>): T? =
    ActionManager.getInstance().getAction(clazz.name)?.let {
        if (it.javaClass.isAssignableFrom(clazz)) clazz.cast(it) else null
    }

fun openPopup(project: Project, node: DebuggerNode, mouseEvent: MouseEvent) {
    val dataContext = dataContextForNode(project, node)
    actionFind(DebuggerActionGroup::class.java)?.run {
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("Debugger", this)
        popupMenu.setDataContext { dataContext }
        popupMenu.component.show(mouseEvent.component, mouseEvent.x, mouseEvent.y)
    }
}

fun dataContextForNode(project: Project, node: DebuggerNode): DataContext =
    when (node) {
        is FolderNode -> newDataContext(project, DEBUGGER_NODE_KEY, node)
        is SourceFile -> newDataContext(project, SOURCE_FILE_KEY, node)
        is ProcessInfo -> newDataContext(project, PROCESS_INFO_KEY, node)
    }

fun <T> newDataContext(project: Project, key: DataKey<T>, value:  T): DataContext =
    SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(key, value).build()
