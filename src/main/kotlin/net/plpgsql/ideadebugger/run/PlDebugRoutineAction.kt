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

package net.plpgsql.ideadebugger.run

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dialects.postgres.model.PgRoutine
import com.intellij.database.psi.DbRoutine
import com.intellij.database.util.DbImplUtilCore
import com.intellij.database.util.ObjectPaths
import com.intellij.database.util.SearchPath
import com.intellij.database.view.getSelectedDbElements
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import icons.PlDebuggerIcons
import net.plpgsql.ideadebugger.*
import net.plpgsql.ideadebugger.command.PlApiStackFrame
import net.plpgsql.ideadebugger.command.PlExecutor
import net.plpgsql.ideadebugger.service.PlProcessWatcher
import net.plpgsql.ideadebugger.vfs.PlSourceManager

/**
 * Run debug action from the database tree view
 * This action is not available when
 */
class PlDebugRoutineAction : AnAction() {

    private var routineToDebug: PgRoutine? = null

    private var localDataSource: LocalDataSource? = null

    private var searchPath: SearchPath? = null

    private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

    override fun update(e: AnActionEvent) {

        val p = e.presentation
        val ready = watcher.getDebugMode() != DebugMode.DIRECT

        if (ready) {
            getDbRoutine(e)?.let { routine ->
                routineToDebug = (routine.delegate as PgRoutine)
                e.project?.let { project ->
                    val ds = DbImplUtilCore.getDbDataSource(project, routine.dataSource)
                    localDataSource = DbImplUtilCore.getMaybeLocalDataSource(ds)
                    val searchPathObject = DbImplUtilCore.getSearchPathObjectForSwitch(routine.dataSource, routine)
                    searchPath = ObjectPaths.searchPathOf(searchPathObject)
                }
            }
        }

        p.isVisible = ready
                && localDataSource != null
                && localDataSource!!.dbms.isPostgres
        p.isEnabled = ready
                && routineToDebug != null
                && routineToDebug!!.objectId != watcher.getFunctionOid()

        p.text = if (debugWaiting()) "Open Routine" else "Debug Routine"
        p.icon = PlDebuggerIcons.DebugAction
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }


    private fun getDbRoutine(event: AnActionEvent): DbRoutine? {
        return event.dataContext.getSelectedDbElements(DbRoutine::class.java).single()
    }

    private fun debugWaiting(): Boolean = watcher.getProcess()?.executor?.waiting?.get() ?: false


    override fun actionPerformed(e: AnActionEvent) {
        if (e.project != null && localDataSource != null && routineToDebug != null) {
            runDebugger(e.project!!, localDataSource!!, routineToDebug!!)
        }
    }


    private fun runDebugger(project: Project, dataSource: LocalDataSource, routine: PgRoutine) {

        val settings = getSettings()
        val callDef = CallDefinition(routine)
        val watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

        // Just open source file
        if (watcher.isDebugging()) {
            if(watcher.getFunctionOid() != callDef.oid) {
                watcher.getProcess()?.let { process ->
                    val frame = PlApiStackFrame(1, callDef.oid, 0, "")
                    if (debugWaiting()) {
                        val executor = PlExecutor(
                            getAuxiliaryConnection(
                                project = project,
                                connectionPoint = dataSource,
                                searchPath = searchPath
                            )
                        )
                        PlSourceManager(project, executor).update(frame)
                        executor.cancelAndCloseConnection()
                    } else {
                        process.executor.setGlobalBreakPoint(callDef.oid)
                        process.fileManager.update(frame)
                    }
                }
            }
            return
        }

        //Starts the debugger
        if (callDef.canStartDebug()) {

            val executor = PlExecutor(
                getAuxiliaryConnection(
                    project = project,
                    connectionPoint = dataSource,
                    searchPath = searchPath
                )
            )

            val diag = executor.checkDebugger()
            if (settings.failExtension || !extensionOk(diag)) {
                showExtensionDiagnostic(project, diag)
                executor.cancelAndCloseConnection()
                return
            }

            val manager = XDebuggerManager.getInstance(project)
            manager.startSessionAndShowTab(
                "${routine.name}[${routine.objectId}]",
                null,
                object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession): XDebugProcess {
                        val process = PlProcess(session = session, executor = executor)
                        process.fileManager.update(PlApiStackFrame(1, callDef.oid, 0, ""))
                        process.startDebug(callDef)
                        return process
                    }
                }
            )
        }
    }

}
