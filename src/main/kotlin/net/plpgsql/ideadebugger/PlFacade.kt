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
import com.intellij.psi.PsiManager
import com.intellij.sql.psi.SqlStatement
import net.plpgsql.ideadebugger.service.PlProcessWatcher
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState

class PlFacade : SqlDebuggerFacade {

    private val logger = getLogger(PlFacade::class)
    private var callDef: Pair<DebugMode, PsiElement?> = Pair(DebugMode.NONE, null)

    private var callDefinition = CallDefinition(DebugMode.NONE, null)

    private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {
        if (watcher.isDebugging()) {
            return false
        }
        callDef = getCallStatement(statement, PlDebuggerSettingsState.getInstance().state)
        callDefinition.debugMode = callDef.first
        callDefinition.psi = callDef.second
        return callDefinition.canDebug()
    }

    override fun isApplicableToDebugRoutine(basicSourceAware: BasicSourceAware): Boolean {
        return true
    }

    override fun canDebug(ds: LocalDataSource): Boolean{
        return checkDataSource(ds)
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
        logger.debug("createController")
        val selectedElement = rangeMarker?.let {
            PsiManager.getInstance(project)
                .findFile(virtualFile as VirtualFile)
                ?.findElementAt(it.startOffset)?.parent
        }
        if (selectedElement != callDefinition.psi?.parent) {
            throw Exception("Invalid selection")
        }
        return PlController(
            facade = this,
            project = project,
            connectionPoint = connection,
            ownerEx = owner,
            searchPath = searchPath,
            virtualFile = virtualFile,
            rangeMarker = rangeMarker,
            callDefinition = callDefinition,
        )
    }

    /**
     * Only check it's postgres
     */
    private fun checkDataSource(ds: LocalDataSource): Boolean {
        return ds.dbms.isPostgres
    }

}