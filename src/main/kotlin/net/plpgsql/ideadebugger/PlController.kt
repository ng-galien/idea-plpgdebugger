/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.SearchPath
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import net.plpgsql.ideadebugger.run.PlProcess
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState

class PlController(
    val facade: PlFacade,
    val project: Project,
    val connectionPoint: DatabaseConnectionPoint,
    val ownerEx: DataRequest.OwnerEx,
    val virtualFile: VirtualFile?,
    val rangeMarker: RangeMarker?,
    val searchPath: SearchPath?,
    val callDefinition: CallDefinition,
) : SqlDebugController() {

    private lateinit var plProcess: PlProcess
    lateinit var xSession: XDebugSession
    internal val windowLister = ToolListener()
    private val settings = PlDebuggerSettingsState.getInstance().state
    private var executor: PlExecutor? = null


    override fun getReady() {
        console("Controller: getReady")
        executor?.let { Disposer.register(xSession.consoleView, it) }
        project.messageBus.connect(xSession.consoleView).subscribe(ToolWindowManagerListener.TOPIC, windowLister)
    }


    override fun initLocal(session: XDebugSession): XDebugProcess {
        xSession = session
        executor = PlExecutor(getAuxiliaryConnection(project, connectionPoint, searchPath))

        plProcess = PlProcess(session, executor!!)

        if (settings.enableDebuggerCommand) {
            executor!!.executeSessionCommand(settings.debuggerCommand)
            if (executor!!.interrupted()) {
                return plProcess
            }
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
            windowLister.close()
            Disposer.dispose(DatabaseSessionManager.getSession(project, connectionPoint))
        }
    }

    inner class PortReached : Exception("Port reached")

    inner class ToolListener : ToolWindowManagerListener {

        private var debugWindow: ToolWindow? = null
        private var first: Boolean = false
        private var acutal: Content? = null
        var hasShown = false

        override fun toolWindowShown(toolWindow: ToolWindow) {
            if (toolWindow.id == "Debug") {
                debugWindow = toolWindow
                first = true
            }
            if (first && toolWindow.id != "Debug") {
                debugWindow?.show()
                acutal = debugWindow?.contentManager?.contents?.find {
                    it.tabName == xSession.sessionName
                }
                first = false
                hasShown = true
            }
        }

        fun close() {
            runInEdt {
                windowLister.acutal?.let { debugWindow?.contentManager?.removeContent(it, true) }
            }
        }
    }

}


