package net.plpgsql.ideadebugger.action

import com.intellij.openapi.actionSystem.*


class DebuggerActionGroup : DefaultActionGroup() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = true
    }

}