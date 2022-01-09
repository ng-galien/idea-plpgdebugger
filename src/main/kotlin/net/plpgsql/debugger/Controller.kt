package net.plpgsql.debugger

import com.intellij.database.connection.throwable.info.WarningInfo
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.datagrid.DataAuditors
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.SearchPath
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlExpressionList
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlIdentifier
import com.intellij.sql.psi.SqlLiteralExpression
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.runBlocking
import java.util.regex.Pattern

class Controller(
    val project: Project,
    val connectionPoint: DatabaseConnectionPoint,
    val ownerEx: DataRequest.OwnerEx,
    val virtualFile: VirtualFile?,
    val rangeMarker: RangeMarker?,
    val searchPath: SearchPath?,
    val callExpression: SqlFunctionCallExpression?,
) : SqlDebugController() {

    //private val logger = KotlinLogging.logger {}
    private val pattern: Pattern = Pattern.compile(".*PLDBGBREAK:([0-9]+).*")
    private lateinit var process: Process

    override fun getReady() {
        //logger.info { "getReady" }
    }

    override fun initLocal(session: XDebugSession): XDebugProcess {
        //logger.info { "initLocal" }
        val manager = XDebuggerManager.getInstance(project)
        ownerEx.messageBus.addAuditor(DataAdapter())
        process = Process(session, connectionPoint)
        return process
    }

    override fun initRemote(databaseConnection: DatabaseConnection) {
        //logger.info { "initRemote" }
        runBlocking {
            searchFunction(databaseConnection)
        }
    }

    override fun debugBegin() {
        //ogger.info { "initRemote" }
    }

    override fun debugEnd() {
        //logger.info { "debugEnd" }
    }

    override fun close() {
        //logger.info { "close" }
    }

    inner class DataAdapter : DataAuditors.Adapter() {
        override fun warn(context: DataRequest.Context, info: WarningInfo) {
            //logger.info { info.message }
            val matcher = pattern.matcher(info.message)
            if (matcher.matches()) {
                val port = matcher.group(1).toInt()
            }
        }
    }

    private suspend fun searchFunction(c: DatabaseConnection) {
        val id = PsiTreeUtil.findChildOfType(callExpression, SqlIdentifier::class.java)!!
        var fid = id.text.split("\\.".toRegex()).toTypedArray()

        if (fid.size != 2) {
            fid = arrayOf("public", id.text)
        }
        val def = plSearchFunction(c, fid[0], fid[1])

        val psiArgList = PsiTreeUtil.findChildOfType(callExpression, SqlExpressionList::class.java)
        var psiArgMap: Map<String, String>? = null
        if (psiArgList != null) {
            psiArgMap = PsiTreeUtil.findChildrenOfAnyType(psiArgList, SqlLiteralExpression::class.java)
                .toList().associate { it.node.text to it.node.elementType.debugName }
        }
        if (psiArgMap != null) {

        }
        val oid = def.first().oid
        val res = plDebug(c, oid)
        if(res != 0) {
            throw Exception("Start debug failed")
        }

    }

}


