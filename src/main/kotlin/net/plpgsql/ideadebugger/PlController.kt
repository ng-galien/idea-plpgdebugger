/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.DGDepartment
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.ErrorHandler
import com.intellij.database.util.GuardedRef
import com.intellij.database.util.SearchPath
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.ui.content.Content
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.*
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState

class PlController(
    val facade: PlFacade,
    val project: Project,
    val connectionPoint: DatabaseConnectionPoint,
    val ownerEx: DataRequest.OwnerEx,
    val virtualFile: VirtualFile?,
    val rangeMarker: RangeMarker?,
    val searchPath: SearchPath?,
    val callExpression: SqlFunctionCallExpression?,
) : SqlDebugController() {

    private lateinit var plProcess: PlProcess
    lateinit var xSession: XDebugSession
    internal val windowLister = ToolListener()

    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        executor.setError("CoroutineExceptionHandler got", e)
        xSession.stop()
    }

    val scope = CoroutineScope(Dispatchers.Default + exceptionHandler)
    var timeOutJob: Job? = null
    val settings = PlDebuggerSettingsState.getInstance().state
    val executor = PlExecutor(this)

    fun getAuxiliaryConnection(): GuardedRef<DatabaseConnection> {
        return DatabaseSessionManager.getFacade(
            project,
            connectionPoint,
            null,
            searchPath,
            true,
            object : ErrorHandler() {},
            DGDepartment.DEBUGGER
        ).connect()
    }

    fun closeFile(file: PlFunctionSource?) {
        if (file == null) {
            return
        }
        runInEdt {
            val editorManager = FileEditorManagerEx.getInstanceEx(project)
            editorManager.closeFile(file)
        }
    }

    fun checkFile(file: PlFunctionSource?) {
        file?.let { source ->
            runReadAction {
                PsiManager.getInstance(project).findFile(source)?.let { psi ->
                    PsiDocumentManager.getInstance(project).getDocument(psi)?.let { doc ->
                        if (doc.text != source.content) {
                            runInEdt {
                                runWriteAction {
                                    doc.setText(source.content)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getReady() {
        console("Controller: getReady")
        Disposer.register(xSession.consoleView, executor)
        project.messageBus.connect(xSession.consoleView).subscribe(ToolWindowManagerListener.TOPIC, windowLister)
    }


    override fun initLocal(session: XDebugSession): XDebugProcess {
        xSession = session
        plProcess = PlProcess(this)

        if (settings.enableDebuggerCommand) {
            executor.executeSessionCommand(settings.debuggerCommand)
            if (executor.interrupted()) {
                return plProcess
            }
        }

        val diag = executor.checkDebugger()
        if (settings.failExtension || !extensionOk(diag)) {
            showExtensionDiagnostic(project, diag)
            executor.cancelAndCloseConnection()
            return plProcess
        }

        if (callExpression != null) {
            val callDef = parseFunctionCall(callExpression)
            assert(callDef.first.isNotEmpty() && callDef.first.size <= 2) { "Error while parsing ${callExpression.text}" }
            executor.searchCallee(callDef.first, callDef.second)
            if (executor.interrupted()) {
                return plProcess
            }
        } else {
            executor.setError("Invalid call expression")
            executor.interrupted()
        }
        plProcess.startDebug()
        return plProcess
    }

    override fun initRemote(connection: DatabaseConnection) {
        executor.setDebug("Controller: initRemote")

        /*if (settings.enableSessionCommand) {
            executor.executeSessionCommand(rawSql = settings.sessionCommand, connection = connection)
            if (executor.interrupted()) {
                return
            }
        }
        */

    }

    private suspend fun waitForPort(ownerConnection: DatabaseConnection) {
        kotlin.runCatching {
            withTimeout(settings.attachTimeOut.toLong()) {
                repeat(3) {
                    delay(settings.attachTimeOut.toLong())
                }
            }
        }.onFailure {
            when (it) {
                is TimeoutCancellationException -> {
                    //executor.abort()
                    executor.setError("Attachment timeout reached (${settings.attachTimeOut}ms)")
                    executor.interrupted()
                    xSession.stop()
                    kotlin.runCatching {
                        ownerConnection.remoteConnection.close()
                    }.onFailure { e ->
                        println("$e")
                    }
                }
                else -> executor.setInfo("Port reached, discard timeout")
            }
        }

    }

    override fun debugBegin() {
        console("Controller: debugBegin")
    }


    override fun debugEnd() {
        console("controller: debugEnd")
        xSession.stop()
    }

    override fun close() {
        console("Controller: close")
        windowLister.close()
        Disposer.dispose(DatabaseSessionManager.getSession(project, connectionPoint))
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
                executor.displayInfo()
            }
        }

        fun close() {
            runInEdt {
                windowLister.acutal?.let { debugWindow?.contentManager?.removeContent(it, true) }
            }
        }
    }

}


