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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
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

    private val logger = logger<PlDebugRoutineAction>()
    private var routineToDebug: PgRoutine? = null

    private var localDataSource: LocalDataSource? = null

    private var searchPath: SearchPath? = null

    private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

    override fun update(e: AnActionEvent) {

        val p = e.presentation
        val ready = watcher.getDebugMode() != DebugMode.DIRECT && !watcher.isInitializing()

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
        val project = e.project ?: return
        val dataSource = localDataSource ?: return
        val routine = routineToDebug ?: return
        val currentSearchPath = searchPath
        val openExistingDebug = watcher.isDebugging()
        val ownsInitialization = !openExistingDebug && watcher.tryReserveInitialization()
        if (!openExistingDebug && !ownsInitialization) {
            return
        }
        object : Task.Backgroundable(project, "Getting Auxiliary Connection", true) {
            override fun run(indicator: ProgressIndicator) {
                if (indicator.isCanceled) {
                    if (ownsInitialization) {
                        watcher.releaseInitialization()
                    }
                    return
                }
                try {
                    runDebugger(
                        project,
                        dataSource,
                        routine,
                        currentSearchPath,
                        openExistingDebug,
                        ownsInitialization,
                    )
                } catch (error: Throwable) {
                    if (ownsInitialization) {
                        watcher.releaseInitialization()
                    }
                    throw error
                }
            }
        }.queue()
    }


    private fun runDebugger(
        project: Project,
        dataSource: LocalDataSource,
        routine: PgRoutine,
        currentSearchPath: SearchPath?,
        openExistingDebug: Boolean,
        ownsInitialization: Boolean,
    ) {

        val settings = getSettings()
        val callDef = CallDefinition(routine)
        val watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

        // Just open source file
        if (openExistingDebug) {
            if(watcher.getFunctionOid() != callDef.oid) {
                watcher.getProcess()?.let { process ->
                    val frame = PlApiStackFrame(1, callDef.oid, 0, "")
                    if (debugWaiting()) {
                        val connection = getAuxiliaryConnection(
                            project = project,
                            connectionPoint = dataSource,
                            searchPath = currentSearchPath
                        )
                        connection?.let {
                            val executor = PlExecutor(connection)
                            PlSourceManager(project, executor).update(frame)
                            executor.cancelAndCloseConnection()
                        }
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
            val connection = getAuxiliaryConnection(
                project = project,
                connectionPoint = dataSource,
                searchPath = currentSearchPath
            )
            if(connection == null) {
                if (ownsInitialization) {
                    watcher.releaseInitialization()
                }
                return
            }
            val executor = PlExecutor(connection)
            val diag = executor.checkDebugger()
            if (settings.failExtension || !extensionOk(diag)) {
                showExtensionDiagnostic(project, diag)
                executor.cancelAndCloseConnection()
                if (ownsInitialization) {
                    watcher.releaseInitialization()
                }
                return
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    executor.cancelAndCloseConnection()
                    if (ownsInitialization) {
                        watcher.releaseInitialization()
                    }
                    return@invokeLater
                }
                try {
                    XDebuggerManager.getInstance(project)
                        .newSessionBuilder(object : XDebugProcessStarter() {
                        override fun start(session: XDebugSession): XDebugProcess {
                            val process = PlProcess(session = session, initialExecutor = executor)
                            ApplicationManager.getApplication().executeOnPooledThread {
                                try {
                                    process.fileManager.update(PlApiStackFrame(1, callDef.oid, 0, ""))
                                    if (!process.startDebug(callDef)) {
                                        executor.cancelAndCloseConnection()
                                        if (ownsInitialization) {
                                            watcher.releaseInitialization()
                                        }
                                    }
                                } catch (error: Throwable) {
                                    executor.cancelAndCloseConnection()
                                    if (ownsInitialization) {
                                        watcher.releaseInitialization()
                                    }
                                    logger.error("Unable to start the PL/pg debugger process", error)
                                }
                            }
                            return process
                        }
                    })
                        .sessionName("${routine.name}[${routine.objectId}]")
                        .showTab(true)
                        .startSession()
                } catch (error: Throwable) {
                    executor.cancelAndCloseConnection()
                    if (ownsInitialization) {
                        watcher.releaseInitialization()
                    }
                    logger.error("Unable to create the PL/pg debugger session", error)
                }
            }
        } else if (ownsInitialization) {
            watcher.releaseInitialization()
        }
    }

}
