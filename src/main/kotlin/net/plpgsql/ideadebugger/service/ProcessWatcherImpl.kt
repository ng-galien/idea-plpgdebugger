/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.service

import com.intellij.openapi.components.Service
import net.plpgsql.ideadebugger.DebugMode
import net.plpgsql.ideadebugger.command.EMPTY_ENTRY_POINT
import net.plpgsql.ideadebugger.command.INVALID_SESSION
import net.plpgsql.ideadebugger.console
import net.plpgsql.ideadebugger.node.ProcessInfo
import net.plpgsql.ideadebugger.run.PlProcess

@Service
class ProcessWatcherImpl: ProcessWatcher {

    private var currentProcess: PlProcess? = null
    private var mode: DebugMode = DebugMode.NONE
    private var oid: Long = EMPTY_ENTRY_POINT
    private var session = INVALID_SESSION

    override fun getDebugMode(): DebugMode {
        return mode
    }

    override fun getFunctionOid(): Long {
        return oid
    }

    override fun getDbInternalSession(): Int {
        return session
    }

    override fun isDebugging(): Boolean {
        return mode != DebugMode.NONE
    }

    override fun processStarted(process: PlProcess, debugMode: DebugMode, functionOid: Long) {
        console("Watcher: started")
        currentProcess = process
        mode = debugMode
        oid = functionOid
        session = process.executor.getDbInternalSession()
        publishEvent(ProcessCreated(process.project, ProcessInfo(session, mode)))
    }

    override fun processFinished(process: PlProcess) {
        console("Watcher: finished")
        publishEvent(ProcessFinished(process.project, ProcessInfo(session, mode)))
        currentProcess = null
        mode = DebugMode.NONE
        oid = 0L
    }

    override fun getProcess(): PlProcess? {
        return currentProcess
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProcessWatcherImpl

        return session == other.session
    }

    override fun hashCode(): Int {
        return session
    }


}