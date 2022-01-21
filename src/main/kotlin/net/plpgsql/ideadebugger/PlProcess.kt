/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.*

class PlProcess(
    var controller: PlController
) : SqlDebugProcess(controller.xSession) {

    private val context = XContext()
    private val fakeStep = PlStep(-1, 0)
    private val executor = controller.executor
    private var step: PlStep = fakeStep
    private var stack: XStack = XStack(this)
    private var breakPointHandler = BreakPointHandler()
    private val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }

    private val scope = CoroutineScope(Dispatchers.EDT+handler)

    init {
        val manager = XDebuggerManager.getInstance(session.project).breakpointManager
        //manager.addBreakpointListener(PlLineBreakpointType.INSTANCE, breakPointHandler)
        if (PlVFS.getInstance().count() == 0) {
            runWriteAction {
                manager.allBreakpoints.filter {
                    it.type is PlLineBreakpointType
                }.forEach {
                    manager.removeBreakpoint(it)
                }

            }
        }
    }

    override fun startStepOver(context: XSuspendContext?) {
        executor.setInfo("User request: startStepOver")
        scope.launch() { goToStep(SQLQuery.STEP_OVER)}

    }

    override fun startStepInto(context: XSuspendContext?) {
        executor.setInfo("User request: startStepInto")
        scope.launch()  { goToStep(SQLQuery.STEP_INTO)}
    }

    override fun resume(context: XSuspendContext?) {
        executor.setInfo("User request: resume")
        scope.launch()  { goToStep(SQLQuery.STEP_CONTINUE)}
    }

    fun startDebug() {
        executor.setInfo("From auxiliary request: startDebug")
        handleStackStatus(stack.updateRemote(step))
    }

    override fun startForceStepInto(context: XSuspendContext?) {
        executor.setInfo("User request mot supported: use startStepInto")
        scope.launch()  { goToStep(SQLQuery.STEP_INTO) }

    }

    override fun startStepOut(context: XSuspendContext?) {
        executor.setInfo("User request mot supported: use resume")
        scope.launch()  { goToStep(SQLQuery.STEP_CONTINUE)}
    }

    override fun stop() {
        executor.setInfo("User request: stop")
        if (!executor.interrupted) {
            executor.abort()
        }
    }

    private fun handleStackStatus(file: PlFile) {
        if (file.canContinue()) {
            scope.launch()  { goToStep(SQLQuery.STEP_CONTINUE)}
        } else {
            if (file.isOnBreakpoint()) {
                session.positionReached(context)
            } else {
                session.positionReached(context)
            }
        }
    }


    private suspend fun goToStep(cmd: SQLQuery) = coroutineScope {
        withTimeout(1000) {
            step = executor.runStep(cmd) ?: fakeStep
            if (!executor.interrupted) {
                handleStackStatus(stack.updateRemote(step))
            }
        }

    }


    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return PlEditorProvider.INSTANCE
    }

    override fun checkCanInitBreakpoints(): Boolean {
        return executor.ready()
    }

    override fun checkCanPerformCommands(): Boolean {
        return executor.ready()
    }

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> {
        return arrayOf(breakPointHandler)
    }

    inner class XContext : XSuspendContext() {

        override fun getActiveExecutionStack(): XExecutionStack {
            return stack
        }

        override fun getExecutionStacks(): Array<XExecutionStack> {
            return arrayOf(stack)
        }

        override fun computeExecutionStacks(container: XExecutionStackContainer?) {
            container?.addExecutionStack(mutableListOf(stack), true)
        }
    }


    inner class BreakPointHandler() :
        XBreakpointHandler<XLineBreakpoint<PlLineBreakpointProperties>>(PlLineBreakpointType::class.java),
        XBreakpointListener<XLineBreakpoint<PlLineBreakpointProperties>> {

        override fun registerBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
            breakpoint.properties?.file.let {
                executor.setInfo("registerBreakpoint: ${breakpoint.fileUrl} => ${breakpoint.line}")
                if ((it as PlFile).addSourceBreakpoint(breakpoint.line)) {
                    session.setBreakpointVerified(breakpoint)
                }
            }
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>, temporary: Boolean) {
            breakpoint.properties.file.let {
                executor.setInfo("unregisterBreakpoint: ${breakpoint.fileUrl} => ${breakpoint.line}")
                (it as PlFile).removeSourceBreakpoint(breakpoint.line)
            }
        }

    }



}