/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.run

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dialects.postgres.model.PgRoutine
import com.intellij.database.psi.DbRoutine
import com.intellij.database.util.DbImplUtilCore
import com.intellij.database.view.getSelectedDbElements
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import icons.PlDebuggerIcons
import net.plpgsql.ideadebugger.*
import net.plpgsql.ideadebugger.service.PlProcessWatcher


class PlDebugRoutineAction : AnAction() {

    private var routineToDebug: PgRoutine? = null

    private var localDataSource: LocalDataSource? = null

    private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)


    override fun update(e: AnActionEvent) {
        val p = e.presentation
        var ready = !watcher.isDebugging()
        if (ready) {
            val routine = getDbRoutine(e)

            routineToDebug = (routine?.delegate as PgRoutine)
            e.project?.let { project ->
                val ds = DbImplUtilCore.getDbDataSource(project, routine.dataSource)
                localDataSource = DbImplUtilCore.getMaybeLocalDataSource(ds)
            }
        }
        ready = localDataSource != null && localDataSource!!.dbms.isPostgres && routineToDebug != null
        p.isVisible = ready
        p.isEnabled = ready
        p.text = "Debug Routine"
        p.icon = PlDebuggerIcons.DebugAction
    }

    private fun getDbRoutine(event: AnActionEvent): DbRoutine? {
        return event.dataContext.getSelectedDbElements(DbRoutine::class.java).single()
    }


    override fun actionPerformed(e: AnActionEvent) {
        val settings = getSettings()
        val project = e.project
        val ds = localDataSource
        val routine = routineToDebug
        if (project != null && ds != null && routine != null) {

            val callDef = CallDefinition(routine)
            val watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)

            // Just open source file
            if (watcher.isDebugging()) {
                watcher.getProcess()?.executor?.let {
                    openFile(project, it, callDef)
                }
                return
            }

            //Starts the debugger
            if (callDef.canStartDebug()) {
                val executor = PlExecutor(getAuxiliaryConnection(
                    project = project,
                    connectionPoint = ds,
                    searchPath = null)
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
                        process.startDebug(callDef)
                        openFile(project, executor, callDef)
                        return process
                    }
                })
            }
        }
    }

    fun openFile(project: Project, executor: PlExecutor, callDef: CallDefinition) {
        val sourceManager = PlSourceManager(project, executor)
        val vfs = sourceManager.update(PlApiStackFrame(0, callDef.oid, 0, ""))
        vfs?.let {
            FileEditorManager.getInstance(project).openFile(vfs, true)
        }
    }
}