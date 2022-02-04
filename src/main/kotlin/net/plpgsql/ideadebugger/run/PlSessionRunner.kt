/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.run

import com.intellij.database.debugger.SqlDebugSessionRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.concurrency.Promise

class PlSessionRunner(override var onFinish: (() -> Unit)?) : SqlDebugSessionRunner {
    override fun execute(environment: ExecutionEnvironment): Promise<RunContentDescriptor?> {
        TODO("Not yet implemented")
    }

}