/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext

class PlProcess(
    val controller: PlController
) : SqlDebugProcess(controller.xSession) {

    private val logger = getLogger<PlProcess>()
    private val context = XContext()
    private val stack = XStack(session)
    private val fakeStep = PlStep(-1, 0, "")

    private var step: PlStep = fakeStep

    init {
        if (PlVFS.getInstance().count() == 0) {
            runWriteAction {
                val manager = XDebuggerManager.getInstance(session.project).breakpointManager
                manager.allBreakpoints.filter {
                    it.type is PlLineBreakpointType
                }.forEach {
                    manager.removeBreakpoint(it)
                }
            }
        }
    }

    override fun startStepOver(context: XSuspendContext?) {
        logger.debug("startStepOver")
        goToStep(SQLQuery.STEP_OVER)

    }

    override fun startStepInto(context: XSuspendContext?) {
        logger.debug("startStepInto")
        goToStep(SQLQuery.STEP_INTO)
    }

    override fun resume(context: XSuspendContext?) {
        logger.debug("resume")
        goToStep(SQLQuery.STEP_CONTINUE)
    }

    fun startDebug() {
        handleStackStatus(stack.updateRemote(step))
    }

    override fun startForceStepInto(context: XSuspendContext?) {
        handleStackStatus(stack.updateRemote(step))
    }

    override fun startStepOut(context: XSuspendContext?) {
        handleStackStatus(stack.updateRemote(step))
    }

    private fun handleStackStatus(file: PlFile) {
        if (file.canContinue()) {
            goToStep(SQLQuery.STEP_CONTINUE)
        } else {
            if (file.isOnBreakpoint()) {
                session.positionReached(context)
            } else {
                session.positionReached(context)
            }
        }
    }

    private fun goToStep(cmd: SQLQuery) {
        step = controller.executor.runStep(cmd) ?: fakeStep
        if (controller.checkExecutor()) {
            handleStackStatus(stack.updateRemote(step))
        }
    }


    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return PlEditorProvider
    }

    override fun checkCanInitBreakpoints(): Boolean = controller.executor.ready()

    override fun checkCanPerformCommands(): Boolean = controller.executor.ready()

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> {
        return arrayOf(
            BreakPointHandler()
        )
    }

    inner class XContext : XSuspendContext() {

        override fun getActiveExecutionStack(): XExecutionStack? {
            return stack
        }

        override fun getExecutionStacks(): Array<XExecutionStack> {
            return arrayOf(stack)
        }

        override fun computeExecutionStacks(container: XExecutionStackContainer?) {
            container?.addExecutionStack(mutableListOf(stack), true)
        }
    }


    inner class BreakPointHandler :
        XBreakpointHandler<XLineBreakpoint<PlLineBreakpointProperties>>(PlLineBreakpointType::class.java),
        XBreakpointListener<XLineBreakpoint<PlLineBreakpointProperties>> {

        override fun registerBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
            breakpoint.properties?.file.let {
                consoleInfo("registerBreakpoint: ${breakpoint.fileUrl} => ${breakpoint.line}")
                if ((it as PlFile).addSourceBreakpoint(breakpoint.line)) {
                    session.setBreakpointVerified(breakpoint)
                }
            }
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>, temporary: Boolean) {
            breakpoint.properties.file.let {
                consoleInfo("unregisterBreakpoint: ${breakpoint.fileUrl} => ${breakpoint.line}")
                (it as PlFile).removeSourceBreakpoint(breakpoint.line)
            }
        }

    }

    private fun consoleInfo(msg: String) =
        runInEdt { session?.consoleView?.print("$msg\n", ConsoleViewContentType.LOG_INFO_OUTPUT) }

}