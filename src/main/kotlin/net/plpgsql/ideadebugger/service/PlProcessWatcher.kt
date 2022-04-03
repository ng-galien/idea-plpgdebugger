/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.service

import net.plpgsql.ideadebugger.DebugMode
import net.plpgsql.ideadebugger.run.PlProcess

interface PlProcessWatcher {
    fun isDebugging(): Boolean
    fun processStarted(process: PlProcess, debugMode: DebugMode, functionOid: Long)
    fun processFinished(process: PlProcess)
    fun getProcess(): PlProcess?
    fun getDebugMode(): DebugMode
    fun getFunctionOid(): Long
}