package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.debugger.SqlDebuggerFacade
import com.intellij.database.model.basic.BasicSourceAware
import com.intellij.database.util.SearchPath
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.*

class PlFacade : SqlDebuggerFacade {

    private val logger = getLogger(PlFacade::class)
    var disableMe: SearchPath? = null
    private var call: SqlFunctionCallExpression? = null

    init {
        logger.debug("PlFacade init")
    }

    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {
        call = null
        //Procedure is discarded here
        if (statement !is SqlSelectStatement) {
            //return false
        }
        val children = PsiTreeUtil.findChildrenOfAnyType(statement, SqlFunctionCallExpression::class.java)
        if (children.size == 1) {
            call = children.first()
        }
        return call != null
    }

    override fun isApplicableToDebugRoutine(basicSourceAware: BasicSourceAware): Boolean {
        return true
    }

    override fun canDebug(ds: LocalDataSource): Boolean = checkConnection(ds)


    override fun createController(
        project: Project,
        connection: DatabaseConnectionPoint,
        owner: DataRequest.OwnerEx,
        scriptIsMeaningful: Boolean,
        virtualFile: VirtualFile?,
        rangeMarker: RangeMarker?,
        searchPath: SearchPath?,
    ): SqlDebugController {
        return PlController(
            facade = this,
            project = project,
            connectionPoint = connection,
            ownerEx = owner,
            searchPath = searchPath,
            virtualFile = virtualFile,
            rangeMarker = rangeMarker,
            callExpression = call,
        )
    }
//(disableMe as LocalDataSource).localDataSource.schemaMapping 54724
    private fun checkConnection(ds: LocalDataSource): Boolean {
        return ds.dbms.isPostgres
    }

}