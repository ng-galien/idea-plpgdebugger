/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.SearchPath
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import net.plpgsql.ideadebugger.command.PlExecutor
import net.plpgsql.ideadebugger.run.PlProcess
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState

class PlController(
    val project: Project,
    val connectionPoint: DatabaseConnectionPoint,
    val searchPath: SearchPath?,
    val callDefinition: CallDefinition,
) : SqlDebugController() {

    private lateinit var plProcess: PlProcess
    lateinit var xSession: XDebugSession
    private val settings = PlDebuggerSettingsState.getInstance().state
    private var executor: PlExecutor? = null

    override fun getReady() {
        console("Controller: getReady")
        executor?.let { Disposer.register(xSession.consoleView, it) }
        val windowLister = ToolListener()
        project.messageBus.connect(xSession.consoleView).subscribe(ToolWindowManagerListener.TOPIC, windowLister)
    }

    override fun initLocal(session: XDebugSession): XDebugProcess {
        xSession = session
        executor = PlExecutor(getAuxiliaryConnection(project, connectionPoint, searchPath))

        plProcess = PlProcess(session, executor!!)
        @Suppress("DialogTitleCapitalization")
        if (!callDefinition.canDebug()) {
            val notification = Notification(
                "PL/pg Notifications",
                "PL/pg Debugger",
                String.format("You must select only one valid query"),
                NotificationType.WARNING
            )
            notification.notify(project)
            return plProcess
        }

        val diag = executor!!.checkDebugger()
        if (settings.failExtension || !extensionOk(diag)) {
            showExtensionDiagnostic(project, diag)
            executor!!.cancelAndCloseConnection()
            return plProcess
        }

        if (settings.failDetection) {
            executor!!.setError("[FAKE]Function not found: schema=${callDefinition.schema}, name=${callDefinition.routine}")
        }

        if (executor!!.interrupted()) {
            return plProcess
        }

        callDefinition.identify(executor!!)

        if (!callDefinition.canStartDebug()) {
            executor!!.setError("[FAKE]Function not found: schema=${callDefinition.schema}, name=${callDefinition.routine}")
        }

        if (executor!!.interrupted()) {
            return plProcess
        }

        plProcess.startDebug(callDefinition)
        return plProcess
    }

    override fun initRemote(connection: DatabaseConnection) {

    }

    override fun debugBegin() {
        console("Controller: debugBegin")
    }

    override fun debugEnd() {
        console("controller: debugEnd")
        if (callDefinition.debugMode == DebugMode.DIRECT) {
            xSession.stop()
        }
    }

    override fun close() {
        console("Controller: close")
        if (callDefinition.debugMode == DebugMode.DIRECT) {
            closeDebugWindow(xSession.sessionName)
            Disposer.dispose(DatabaseSessionManager.getSession(project, connectionPoint))
        }
    }

    private fun closeDebugWindow(sessionName: String) {
        runInEdt {
            ToolWindowManager.getInstance(project).getToolWindow("Debug")?.let { toolWindow ->
                toolWindow.contentManager.contents.first {
                    it.tabName == sessionName
                }.let {
                    it.manager?.removeContent(it, true)
                }
            }
        }
    }

    inner class ToolListener() : ToolWindowManagerListener {

        private var debugWindow: ToolWindow? = null
        private var first: Boolean = false
        override fun toolWindowShown(toolWindow: ToolWindow) {

            if (toolWindow.id == "Debug") {
                debugWindow = toolWindow
                first = true
            }
            if (first && toolWindow.id != "Debug") {
                debugWindow?.show()
                first = false
            }
        }
    }
}


