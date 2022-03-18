/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.service

import com.intellij.openapi.components.Service
import net.plpgsql.ideadebugger.DebugMode
import net.plpgsql.ideadebugger.console
import net.plpgsql.ideadebugger.run.PlProcess

@Service
class PlProcessWatcherImpl: PlProcessWatcher {

    private var currentProcess: PlProcess? = null
    private var mode: DebugMode = DebugMode.NONE
    private var oid: Long = 0L

    override fun getDebugMode(): DebugMode {
        return mode
    }

    override fun getFunctionOid(): Long {
        return oid
    }

    override fun isDebugging(): Boolean {
        return mode != DebugMode.NONE
    }

    override fun processStarted(process: PlProcess, debugMode: DebugMode, functionOid: Long) {
        console("Watcher: started")
        currentProcess = process
        mode = debugMode
        oid = functionOid
    }

    override fun processFinished(process: PlProcess) {
        console("Watcher: finished")
        currentProcess = null
        mode = DebugMode.NONE
        oid = 0L
    }

    override fun getProcess(): PlProcess? {
        return currentProcess
    }
}