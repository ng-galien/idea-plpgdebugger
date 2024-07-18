/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

/**
 * This class represents the PL Controller which extends the SqlDebugController.
 * It is responsible for managing the PL debugging process.
 *
 * @property project The project associated with the PL controller.
 * @property connectionPoint The database connection point.
 * @property searchPath The search path for the PL controller.
 * @property callDefinition The call definition for the PL controller.
 */
class PlController(
    val project: Project,
    val connectionPoint: DatabaseConnectionPoint,
    val searchPath: SearchPath?,
    val callDefinition: CallDefinition,
) : SqlDebugController() {

    private lateinit var plProcess: PlProcess
    private lateinit var xSession: XDebugSession
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

        // DatabaseTools identification
        callDefinition.identify()

        if (!callDefinition.canStartDebug()) {
            callDefinition.identify(executor!!)
        }

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

    inner class ToolListener : ToolWindowManagerListener {

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


