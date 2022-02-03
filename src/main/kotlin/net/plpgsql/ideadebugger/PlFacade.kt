package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.debugger.SqlDebuggerFacade
import com.intellij.database.model.basic.BasicSourceAware
import com.intellij.database.util.SearchPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.sql.psi.*
import net.plpgsql.ideadebugger.service.PlProcessWatcher
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState
import net.plpgsql.ideadebugger.vfs.PlFunctionSource

class PlFacade : SqlDebuggerFacade {

    private val logger = getLogger(PlFacade::class)
    private var callDef: Pair<DebugMode, PsiElement?> = Pair(DebugMode.NONE, null)
    private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

    init {
        console("PlFacade init")
    }

    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {
        if (watcher.isDebugging()) {
            return false
        }
        if (statement.containingFile.virtualFile is PlFunctionSource) {
            return false
        }
        callDef = getCallStatement(statement, PlDebuggerSettingsState.getInstance().state)
        return callDef.first != DebugMode.NONE && callDef.second != null
    }

    override fun isApplicableToDebugRoutine(basicSourceAware: BasicSourceAware): Boolean {
        return true
    }

    override fun canDebug(ds: LocalDataSource): Boolean{
        return checkConnection(ds)
    }


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
            callExpression = callDef.second!!,
            mode = callDef.first
        )
    }

    //(disableMe as LocalDataSource).localDataSource.schemaMapping 54724
    private fun checkConnection(ds: LocalDataSource): Boolean {
        return ds.dbms.isPostgres
    }


}