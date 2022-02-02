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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.psi.PgCreateFunctionStatementImpl
import com.intellij.sql.dialects.postgres.psi.PgCreateProcedureStatementImpl
import com.intellij.sql.psi.*
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState
import kotlin.test.assertNotNull

class PlFacade : SqlDebuggerFacade {

    private val logger = getLogger(PlFacade::class)
    private var callElement: PsiElement? = null
    private var mode: DebugMode = DebugMode.DIRECT

    init {
        logger.debug("PlFacade init")
    }

    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {

        callElement = null
        if (statement.containingFile.virtualFile is PlFunctionSource) {
            return false
        }
        //Procedure is discarded here
        when (statement) {
            is PgCreateFunctionStatementImpl,
            is PgCreateProcedureStatementImpl -> {
                val settings = PlDebuggerSettingsState.getInstance().state
                if (settings.enableIndirect){
                    callElement = statement
                    mode = DebugMode.INDIRECT
                }
            }
            is SqlSelectStatement,
            is SqlCallStatement -> {
                callElement = PsiTreeUtil.findChildrenOfAnyType(statement, SqlFunctionCallExpression::class.java)
                    .firstOrNull()
                mode = DebugMode.DIRECT
            }

        }
        return callElement != null
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
            callExpression = callElement!!,
            mode = mode
        )
    }

    //(disableMe as LocalDataSource).localDataSource.schemaMapping 54724
    private fun checkConnection(ds: LocalDataSource): Boolean {
        return ds.dbms.isPostgres
    }

}