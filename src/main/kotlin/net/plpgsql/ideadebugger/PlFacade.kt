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

    /**
     * Represents a logger for the PlFacade class.
     *
     * @property logger The logger instance.
     *
     * @constructor Creates a new logger instance for the PlFacade class.
     *
     * @param clazz The class to get the logger instance for.
     */
    private val logger = getLogger(PlFacade::class)

    /**
     * Represents a call definition for a routine.
     *
     * @property debugMode The debugging mode for the call.
     * @property psi The PSI element corresponding to the call.
     * @property query The SQL query for the call.
     * @property schema The schema of the call routine.
     * @property routine The name of the call routine.
     * @property oid The object ID of the call routine.
     * @property args The arguments for the call routine.
     * @property selectionOk Indicates if the call is a candidate for selection.
     */
    private var callDefinition = CallDefinition(DebugMode.NONE, null, "")

    /**
     * Represents a watcher for the PlProcess.
     */
    private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

    /**
     * Check if the given debug statement is applicable.
     *
     * @param statement The SQL statement to check.
     * @return true if the statement is applicable to debugging, false otherwise.
     */
    override fun isApplicableToDebugStatement(statement: SqlStatement): Boolean {
        if (watcher.isDebugging()) {
            return false
        }
        callDefinition = getCallStatement(statement)
        return callDefinition.canSelect()
    }

    /**
     * Determines if the given BasicSourceAware object is applicable to a debug routine.
     *
     * @param basicSourceAware The object to check for applicability.
     * @return true if the object is applicable to a debug routine, false otherwise.
     */
    override fun isApplicableToDebugRoutine(basicSourceAware: BasicSourceAware): Boolean {
        return true
    }

    /**
     * Check if debugging is possible for the given local data source.
     *
     * @param ds The local data source to check.
     * @return True if debugging is possible, false otherwise.
     */
    override fun canDebug(ds: LocalDataSource): Boolean{
        return checkDataSource(ds)
    }

    /**
     * Creates a SqlDebugController for managing the PL debugging process.
     *
     * @param project The project associated with the PL controller.
     * @param connectionPoint The database connection point.
     * @param consoleRequestOwner The owner of the console request.
     * @param scriptIsMeaningful A flag indicating whether the script is meaningful.
     * @param virtualFile The virtual file associated with the range marker.
     * @param rangeMarker The range marker that represents the code selection.
     * @param searchPath The search path for the PL controller.
     * @return The created SqlDebugController.
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
     * Creates an instance of SqlDebugController.
     *
     * @param p0 The project.
     * @param p1 The database connection point.
     * @param p2 The data request owner.
     * @param p3 Indicates if the script is meaningful.
     * @param p4 The virtual file.
     * @param p5 The range marker.
     * @param p6 The search path.
     * @return The created SqlDebugController instance.
     * @deprecated Deprecated in Java.
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
     * Checks if the provided LocalDataSource uses a Postgres database management system.
     *
     * @param ds The LocalDataSource to check.
     * @return `true` if the LocalDataSource uses Postgres, `false` otherwise.
     */
    private fun checkDataSource(ds: LocalDataSource): Boolean {
        return ds.dbms.isPostgres
    }
}
