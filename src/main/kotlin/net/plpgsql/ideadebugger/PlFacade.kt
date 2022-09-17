/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.console.client.DatabaseSessionClient
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.debugger.SqlDebuggerFacade
import com.intellij.database.model.basic.BasicSourceAware
import com.intellij.database.util.SearchPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.sql.psi.SqlStatement
import net.plpgsql.ideadebugger.service.PlProcessWatcher
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState

/**
 * Implements DatabaseTools debugger facade
 */
class PlFacade : SqlDebuggerFacade {

    private val logger = getLogger(PlFacade::class)

    private var callDefinition = CallDefinition(DebugMode.NONE, null, "")

    private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

    /**
     * Checks if the selected statement is a good candidate for direct debugging
     */
    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {
        if (watcher.isDebugging()) {
            return false
        }
        callDefinition = getCallStatement(statement, PlDebuggerSettingsState.getInstance().state)
        return callDefinition.canSelect()
    }

    /**
     * Note this is not work as expected in DatabaseTools
     */
    override fun isApplicableToDebugRoutine(basicSourceAware: BasicSourceAware): Boolean {
        return true
    }

    /**
     * Checks if we can debug the datasource
     */
    override fun canDebug(ds: LocalDataSource): Boolean{
        return checkDataSource(ds)
    }

    /**
     * Creates a new debug controller
     */
    override fun createController(
        project: Project,
        connectionPoint: DatabaseConnectionPoint,
        consoleRequestOwner: DatabaseSessionClient,
        scriptIsMeaningful: Boolean,
        virtualFile: VirtualFile?,
        rangeMarker: RangeMarker?,
        searchPath: SearchPath?
    ): SqlDebugController {
        logger.debug("createController")

        if (rangeMarker != null && virtualFile != null) {
            FileDocumentManager.getInstance().getDocument(virtualFile)?.let { doc ->
                val text = doc.getText(TextRange(rangeMarker.startOffset, rangeMarker.endOffset))
                callDefinition.selectionOk = (text == callDefinition.query)
            }
        }

        return PlController (
            project = project,
            connectionPoint = connectionPoint,
            searchPath = searchPath,
            callDefinition = callDefinition,
        )
    }

    /**
     * TODO: remove this method when DatabaseTools will be fixed
     */
    @Deprecated("Deprecated in Java")
    override fun createController(
        p0: Project,
        p1: DatabaseConnectionPoint,
        p2: DataRequest.OwnerEx,
        p3: Boolean,
        p4: VirtualFile?,
        p5: RangeMarker?,
        p6: SearchPath?
    ): SqlDebugController {
        TODO("Deprecated")
    }

    /**
     * Only check if it's a postgres datasource
     */
    private fun checkDataSource(ds: LocalDataSource): Boolean {
        return ds.dbms.isPostgres
    }
}