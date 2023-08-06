package net.plpgsql.ideadebugger.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.DebuggerItem
import net.plpgsql.ideadebugger.FileItem
import net.plpgsql.ideadebugger.FilesRoot
import net.plpgsql.ideadebugger.service.DebugWatcher
import java.awt.event.MouseEvent


val DEBUGGER_ITEM_KEY: DataKey<DebuggerItem> = DataKey.create("debuggerItem")

private val debugWatcher = ApplicationManager.getApplication().getService(DebugWatcher::class.java)

//Get a Generic Action instance with the given class name which is the id of the action
 fun <T> actionFind(clazz: Class<T>): T? =
    ActionManager.getInstance().getAction(clazz.name)?.let {
        if (it.javaClass.isAssignableFrom(clazz)) clazz.cast(it) else null
    }

fun openPopup(project: Project, mouseEvent: MouseEvent, debuggerItem: DebuggerItem) {
    val dataContext = dataContextForNode(project, debuggerItem)
    actionFind(DebuggerActionGroup::class.java)?.run {
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("Debugger", this)
        popupMenu.setDataContext { dataContext }
        popupMenu.component.show(mouseEvent.component, mouseEvent.x, mouseEvent.y)
    }
}

fun dataContextForNode(project: Project, node: DebuggerItem): DataContext =
    newDataContext(project, DEBUGGER_ITEM_KEY, node)

fun <T> newDataContext(project: Project, key: DataKey<T>, value:  T): DataContext =
    SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(key, value).build()

class DebuggerActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) = event.run { presentation.isVisible = true }
}


fun isFileItem(event: AnActionEvent) = event.dataContext.getData(DEBUGGER_ITEM_KEY) is FileItem
fun isFilesFolder(event: AnActionEvent) = event.dataContext.getData(DEBUGGER_ITEM_KEY) is FilesRoot
fun isFileItemOrFilesRoot(event: AnActionEvent) = isFileItem(event) || isFilesFolder(event)
//fun isDebugging() = debugProcess() != null

fun fileItemFromEvent(event: AnActionEvent) = event.dataContext.getData(DEBUGGER_ITEM_KEY) as? FileItem
fun filesFolderFromEvent(event: AnActionEvent) = event.dataContext.getData(DEBUGGER_ITEM_KEY) as? FilesRoot

//fun debugProcess(): PlProcess? = debugWatcher.getProcess()

class OpenSourceFileAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) = event.run {
        val ok = isFileItem(event)
        this.presentation.isVisible = ok
        this.presentation.isEnabled = ok
    }

    override fun actionPerformed(event: AnActionEvent) =
        fileItemFromEvent(event)?.let { file ->
            event.project?.let {
//                FileEditorManager.getInstance(it)
//                    .openFile(file.plFunctionSource, true, true)
                Unit
            }
        }?: Unit
}

class RefresAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) = event.run {
//        val ok = isFileItemOrFilesRoot(event) && !isDebugging()
        val ok = isFileItemOrFilesRoot(event)
        this.presentation.isVisible = ok
        this.presentation.isEnabled = ok
    }

    override fun actionPerformed(event: AnActionEvent) =
        fileItemFromEvent(event)?.let { file ->
            event.project?.let {
//                FileEditorManager.getInstance(it)
//                    .openFile(file.plFunctionSource, true, true)
                Unit
            }
        }?: filesFolderFromEvent(event)?.let {
            event.project?.let {
//                refreshVFS()
            }
        }?: Unit
}