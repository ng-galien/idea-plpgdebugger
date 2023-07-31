package net.plpgsql.ideadebugger.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import net.plpgsql.ideadebugger.node.SourceFile
import net.plpgsql.ideadebugger.vfs.showFile


class OpenSourceFileAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    override fun actionPerformed(actionEvent: AnActionEvent) {
        val sourceFile: SourceFile? = actionEvent.dataContext.getData(SOURCE_FILE_KEY)
        sourceFile?.let {
            actionEvent.project?.let { project ->
                showFile(project, it)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.dataContext.getData(SOURCE_FILE_KEY) != null
    }
}