/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.service

import com.intellij.openapi.components.Service
import net.plpgsql.ideadebugger.PlProcess
import net.plpgsql.ideadebugger.console

@Service
class PlProcessWatcherImpl: PlProcessWatcher, PlProcessListener {

    private var currentProcess: PlProcess? = null

    override fun started(process: PlProcess) {
        console("Watcher: started")
        currentProcess = process
    }

    override fun finished(process: PlProcess) {
        console("Watcher: finished")
        currentProcess = null
    }

    override fun isDebugging(): Boolean {
        return currentProcess != null
    }
}