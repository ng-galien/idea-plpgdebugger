/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.service

import net.plpgsql.ideadebugger.PlProcess

interface PlProcessWatcher {
    fun isDebugging(): Boolean
    fun processStarted(process: PlProcess)
    fun processFinished(process: PlProcess)
}