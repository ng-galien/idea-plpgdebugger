/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.DatabaseTopics
import com.intellij.database.connection.throwable.info.WarningInfo
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.datagrid.DataAuditors
import com.intellij.database.datagrid.DataConsumer
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.SearchPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.ui.content.Content
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import java.util.regex.Pattern

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

    private val auditor = QueryAuditor()
    private val consumer = QueryConsumer()
    private val logger = getLogger<PlController>()
    private val pattern = Pattern.compile(".*PLDBGBREAK:([0-9]+).*")
    private lateinit var plProcess: PlProcess
    lateinit var xSession: XDebugSession
    internal val windowLister = ToolListener()

    val executor = PlExecutor(this)
    private var busConnection = project.messageBus.connect()

    init {
        println("controller: init")
        busConnection.subscribe(ToolWindowManagerListener.TOPIC, windowLister)
    }

    override fun getReady() {
        println("controller: getReady")
        executor.checkExtension()
        if (executor.interrupted()) {
            return
        }
        if (callExpression != null) {
            val callDef = parseFunctionCall(callExpression)
            assert(callDef.first.isNotEmpty() && callDef.first.size <= 2) { "Error while parsing ${callExpression.text}" }
            executor.searchCallee(callDef.first, callDef.second)
        } else {
            executor.setError("Invalid call expression")
            executor.interrupted()
        }
    }

    override fun initLocal(session: XDebugSession): XDebugProcess {
        xSession = session
        plProcess = PlProcess(this)
        return plProcess
    }

    override fun initRemote(connection: DatabaseConnection) {
        println("controller: initRemote")
        executor.startDebug(connection)
        if (!executor.interrupted()) {
            ownerEx.messageBus.addAuditor(auditor)
            ownerEx.messageBus.addConsumer(consumer)
        }
    }

    override fun debugBegin() {
        println("controller: debugBegin")
    }


    override fun debugEnd() {
        println("controller: debugEnd")
        busConnection.disconnect()
        runReadAction {
            val vfs = PlVFS.getInstance()
            vfs.all().forEach { it.unload() }
        }
    }

    override fun close() {
        println("controller: close")
        //windowLister.close()

    }

    inner class QueryAuditor : DataAuditors.Adapter() {

        override fun warn(context: DataRequest.Context, info: WarningInfo) {
            println("QueryAuditor: warn")
            if (!executor.hasError() && context.request.owner == ownerEx) {
                val matcher = pattern.matcher(info.message)
                if (matcher.matches()) {
                    val port = matcher.group(1).toInt()
                    executor.attachToPort(port)
                    if (!executor.interrupted()) {
                        plProcess.startDebug()
                    }
                }
            }
        }

        override fun fetchStarted(context: DataRequest.Context, index: Int) {
            if (context.request.owner == ownerEx) {
                executor.terminate()
            }
        }
    }

    inner class QueryConsumer : DataConsumer.Adapter() {
        override fun afterLastRowAdded(context: DataRequest.Context, total: Int) {
            if (context.request.owner == ownerEx) {
                //ownerExConnection?.remoteConnection?.close()
            }
        }

    }

    inner class ToolListener : ToolWindowManagerListener {

        private var window: ToolWindow? = null
        private var first: Boolean = false
        private var acutal: Content? = null
        var hasShown = false

        override fun toolWindowShown(toolWindow: ToolWindow) {
            if (toolWindow.id == "Debug") {
                window = toolWindow
                first = true
            }
            if (first && toolWindow.id != "Debug") {
                window?.show()
                acutal = window?.contentManager?.contents?.find {
                    it.tabName == xSession.sessionName
                }
                first = false
                hasShown = true
                executor.updateInfo()
            }
        }

        fun close() {
            runInEdt {
                windowLister.acutal?.let { window?.contentManager?.removeContent(it, true) }
            }
        }
    }
}


