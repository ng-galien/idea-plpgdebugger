package net.plpgsql.ideadebugger.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import net.plpgsql.ideadebugger.service.DebuggerEvent
import net.plpgsql.ideadebugger.service.DebuggerEventListener

class ExplorerListener: DebuggerEventListener {
    override fun onEvent(event: DebuggerEvent) {
        runInEdt {
            explorerPanel(event.project())?.handleEvent(event)
        }
    }

    private fun explorerPanel(project: Project): ExplorerPanel? {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
        return toolWindow?.contentManager?.let {
            val comps = it.component.components
            return comps.firstOrNull { it is ExplorerPanel }?.let { it as ExplorerPanel }
        }
    }
}