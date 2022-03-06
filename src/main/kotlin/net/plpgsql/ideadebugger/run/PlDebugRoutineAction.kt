/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.run

import com.intellij.database.DatabaseBundle
import com.intellij.database.console.JdbcConsole
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugSessionRunner
import com.intellij.database.dialects.postgres.model.PgRoutine
import com.intellij.database.editor.PerformRoutineFromFileActionBase
import com.intellij.database.model.basic.BasicRoutine
import com.intellij.database.util.DbImplUtilCore
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import net.plpgsql.ideadebugger.CallDefinition
import net.plpgsql.ideadebugger.DebugMode
import net.plpgsql.ideadebugger.PlExecutor
import net.plpgsql.ideadebugger.getAuxiliaryConnection
import org.jetbrains.concurrency.*
import javax.swing.Icon


class PlDebugRoutineAction : PerformRoutineFromFileActionBase(
    DatabaseBundle.messagePointer("action.DatabaseView.DebugRoutine.procedure.text", arrayOfNulls<Any>(0)),
    DatabaseBundle.messagePointer("action.DatabaseView.DebugRoutine.function.text", *arrayOfNulls(0))
) {

    var routineToDebug: PgRoutine? = null
    var localDataSource: LocalDataSource? = null

    override fun update(e: AnActionEvent) {
        val p = e.presentation
        val routine = getDbRoutine(e)
        routineToDebug = (routine?.delegate as PgRoutine)
        e.project?.let { project ->
            val ds = DbImplUtilCore.getDbDataSource(project, routine.dataSource)
            localDataSource = DbImplUtilCore.getMaybeLocalDataSource(ds)
        }
        val ready = localDataSource != null && routineToDebug != null
        p.isVisible = ready
        p.isEnabled = ready
        p.text = "Debug"
        p.icon = getIcon(e, null)
    }

    override fun getIcon(e: AnActionEvent, routine: BasicRoutine?): Icon? {
        return AllIcons.Actions.StartDebugger
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val ds = localDataSource
        val r = routineToDebug
        if (project != null && ds != null && r != null) {

            val callDef = CallDefinition(r)
            if (callDef.canStartDebug()) {
                val executor = PlExecutor(getAuxiliaryConnection(
                    project = project,
                    connectionPoint = ds,
                    searchPath = null)
                )
                val manager = XDebuggerManager.getInstance(project)
                val session = manager.startSessionAndShowTab("Test", null, object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession): XDebugProcess {
                        val process = PlProcess(session = session, executor = executor)
                        process.startDebug(callDef)
                        return process
                    }
                })

            }
        }
    }
}