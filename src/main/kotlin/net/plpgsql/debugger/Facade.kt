package net.plpgsql.debugger

import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.debugger.SqlDebuggerFacade
import com.intellij.database.dialects.postgres.model.PgDatabase
import com.intellij.database.model.basic.BasicSourceAware
import com.intellij.database.util.SearchPath
import com.intellij.database.view.dbModel
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlStatement

class Facade : SqlDebuggerFacade {

    private var call: SqlFunctionCallExpression? = null

    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {
        call = PsiTreeUtil
            .findChildrenOfAnyType(statement, SqlFunctionCallExpression::class.java)
            .first()
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
        return Controller(
            project = project,
            connectionPoint = connection,
            ownerEx = owner,
            searchPath = searchPath,
            virtualFile = virtualFile,
            rangeMarker = rangeMarker,
            callExpression = call,
        )
    }

    private fun checkConnection(ds: LocalDataSource): Boolean {

        /*if (localDataSource.dbms.isPostgres) {
            val pgDB = localDataSource.model.modelRoots.filter {
                it.name == localDataSource.name && it is PgDatabase
            }.first() as PgDatabase
            return pgDB.extensions.find { it.name == "pldbgapi" } != null
        }*/
        return ds.dbms.isPostgres;
    }

}