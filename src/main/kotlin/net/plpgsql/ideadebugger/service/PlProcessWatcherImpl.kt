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
