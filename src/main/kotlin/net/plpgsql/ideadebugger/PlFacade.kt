package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.debugger.SqlDebuggerFacade
import com.intellij.database.model.basic.BasicSourceAware
import com.intellij.database.util.SearchPath
import com.intellij.database.vfs.DatabaseElementVirtualFileDataSourceProvider
import com.intellij.database.vfs.DatabaseElementVirtualFileImpl
import com.intellij.database.vfs.DatabaseElementVirtualFileUtils
import com.intellij.database.vfs.DatabaseVirtualFileSystem
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.sql.psi.SqlSelectStatement
import com.intellij.sql.psi.SqlStatement

class PlFacade : SqlDebuggerFacade {

    private val logger = getLogger(PlFacade::class)
    var disableMe: SearchPath? = null
    private var call: SqlFunctionCallExpression? = null

    init {
        logger.debug("PlFacade init")
    }

    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {
        if (statement !is SqlSelectStatement) {
            return false
        }
        call = PsiTreeUtil
            .findChildrenOfAnyType(statement, SqlFunctionCallExpression::class.java)
            .firstOrNull()
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