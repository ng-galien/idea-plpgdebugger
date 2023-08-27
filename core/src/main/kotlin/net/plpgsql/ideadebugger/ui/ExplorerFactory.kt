package net.plpgsql.ideadebugger.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

const val TOOL_WINDOW_ID = "PL/pgSQL"
class ExplorerFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(ExplorerPanel(project), "", false)
        contentManager.addContent(content)
    }
}