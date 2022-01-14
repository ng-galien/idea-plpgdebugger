package net.plpgsql.debugger

import com.intellij.database.connection.throwable.info.WarningInfo
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.datagrid.DataAuditors
import com.intellij.database.datagrid.DataConsumer
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.SearchPath
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlExpressionList
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlIdentifier
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
    private val queryAuditor = QueryAuditor()
    private val queryConsumer = QueryConsumer()
    private var entryPoint = 0L

    override fun getReady() {
        logger.debug("getReady")
    }

    override fun initLocal(session: XDebugSession): XDebugProcess {

        logger.debug("initLocal")
        entryPoint = searchFunction() ?: 0L
        plProcess = PlProcess(session, connectionPoint, entryPoint)
        return plProcess
    }

    override fun initRemote(databaseConnection: DatabaseConnection) {
        logger.info("initRemote")
        val ready = if (entryPoint != 0L) plDebugFunction(databaseConnection, entryPoint) == 0 else false

        if (!ready) {
            runInEdt {
                Messages.showMessageDialog(
                    project,
                    "Routine not found",
                    "PL Debugger",
                    Messages.getInformationIcon()
                )
            }
            close()
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
    }

    inner class QueryAuditor : DataAuditors.Adapter() {
        override fun warn(context: DataRequest.Context, info: WarningInfo) {
            //logger.info { info.message }
            val matcher = pattern.matcher(info.message)
            if (matcher.matches()) {
                val port = matcher.group(1).toInt()
                plProcess.startDebug(port)
            }
        }
    }

    inner class QueryConsumer : DataConsumer.Adapter() {
        override fun afterLastRowAdded(context: DataRequest.Context, total: Int) {
            plProcess.stop()
            close()
        }
    }

    private fun searchFunction(): Long? {
        val connection = getConnection(project, connectionPoint)
        try {
            val (name, args) = runReadAction {
                val identifier = PsiTreeUtil.findChildOfType(callExpression, SqlIdentifier::class.java)
                val values = PsiTreeUtil.findChildOfType(
                    callExpression,
                    SqlExpressionList::class.java
                )?.children?.map { it.text.trim() }?.filter { it != "" && it != "," && !it.startsWith("--") }

                Pair(first = identifier?.name ?: "", second = values ?: listOf<String>())
            }
            return searchFunctionByName(connection = connection, callable = name, callValues = args)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.remoteConnection.close()
        }
        return null
    }

}


