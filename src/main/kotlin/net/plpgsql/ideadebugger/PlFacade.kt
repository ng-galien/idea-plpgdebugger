/*
 * Copyright (c) 2022. Alexandre Boyer
 */

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
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.sql.psi.SqlStatement
import net.plpgsql.ideadebugger.service.PlProcessWatcher
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState

/**
 *
 */
class PlFacade : SqlDebuggerFacade {

    private val logger = getLogger(PlFacade::class)

    private var callDefinition = CallDefinition(DebugMode.NONE, null, "")

    private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

    /**
     *
     */
    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {
        if (watcher.isDebugging()) {
            return false
        }
        callDefinition = getCallStatement(statement, PlDebuggerSettingsState.getInstance().state)
        return callDefinition.canSelect()
    }

    /**
     *
     */
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

        if (rangeMarker != null && virtualFile != null) {
            FileDocumentManager.getInstance().getDocument(virtualFile)?.let { doc ->
                val text = doc.getText(TextRange(rangeMarker.startOffset, rangeMarker.endOffset))
                callDefinition.selectionOk = (text == callDefinition.query)
            }
        }

        return PlController (
            project = project,
            connectionPoint = connection,
            searchPath = searchPath,
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