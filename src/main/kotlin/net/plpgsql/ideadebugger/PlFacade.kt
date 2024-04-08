/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
        callDefinition = getCallStatement(statement)
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
     * Deprecation must be addressed soon as possible
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
