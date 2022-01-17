/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.connection.throwable.info.WarningInfo
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.datagrid.DataAuditors
import com.intellij.database.datagrid.DataConsumer
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.SearchPath
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.ui.content.Content
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import java.util.regex.Pattern

class PlController(
    val project: Project,
    val connectionPoint: DatabaseConnectionPoint,
    val ownerEx: DataRequest.OwnerEx,
    val virtualFile: VirtualFile?,
    val rangeMarker: RangeMarker?,
    val searchPath: SearchPath?,
    val callExpression: SqlFunctionCallExpression?,
) : SqlDebugController() {

    private val logger = getLogger<PlController>()
    private val pattern = Pattern.compile(".*PLDBGBREAK:([0-9]+).*")
    private lateinit var plProcess: PlProcess
    private lateinit var xSession: XDebugSession
    private val queryAuditor = QueryAuditor()
    private val queryConsumer = QueryConsumer()
    private val windowLister = ToolListener()

    private var dbgConnection = createDebugConnection(project, connectionPoint)
    private var busConnection = project.messageBus.connect()


    override fun getReady() {
        logger.debug("getReady")
    }

    override fun initLocal(session: XDebugSession): XDebugProcess {
        busConnection.subscribe(ToolWindowManagerListener.TOPIC, windowLister)
        xSession = session
        logger.debug("initLocal")
        plProcess = PlProcess(session, dbgConnection)
        plProcess.entryPoint = searchFunction() ?: 0L
        return plProcess
    }

    override fun initRemote(connection: DatabaseConnection) {
        logger.info("initRemote")
        val ready = if (plProcess.entryPoint != 0L) (plDebugFunction(plProcess) == 0) else false

        if (!ready) {
            runInEdt {
                Messages.showMessageDialog(
                    project,
                    "Routine not found",
                    "PL Debugger",
                    Messages.getInformationIcon()
                )
            }
            xSession.stop()
        } else {
            ownerEx.messageBus.addAuditor(queryAuditor)
            ownerEx.messageBus.addConsumer(queryConsumer)
        }
    }

    override fun debugBegin() {
        logger.info("debugBegin")
    }

    override fun debugEnd() {
        logger.info("debugEnd")
    }

    override fun close() {
        logger.info("close")
        //windowLister.close()
        busConnection.disconnect()
        dbgConnection.runCatching {
            dbgConnection.remoteConnection.close()
        }
    }


    private fun searchFunction(): Long? {

        if (callExpression != null) {
            val callDef = parseFunctionCall(callExpression)
            assert(callDef.first.isNotEmpty() && callDef.first.size <= 2) { "Error while parsing ${callExpression.text}" }
            return searchFunctionByName(proc = plProcess,
                callFunc = callDef.first,
                callValues = callDef.second)
        }
        return null
    }

    inner class QueryAuditor : DataAuditors.Adapter() {
        override fun warn(context: DataRequest.Context, info: WarningInfo) {
            if (context.request.owner == ownerEx) {
                val matcher = pattern.matcher(info.message)
                if (matcher.matches()) {
                    val port = matcher.group(1).toInt()
                    plProcess.startDebug(port)
                }
            }
        }
    }

    inner class QueryConsumer : DataConsumer.Adapter() {
        override fun afterLastRowAdded(context: DataRequest.Context, total: Int) {
            if (context.request.owner == ownerEx) {
                close()
            }
        }
    }

    inner class ToolListener : ToolWindowManagerListener {
        private var window: ToolWindow? = null
        private var first: Boolean = false
        private var acutal: Content? = null
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
            }
        }

        fun close() {
            runInEdt {
                windowLister.acutal?.let { window?.contentManager?.removeContent(it, true) }
            }
        }
    }

}


