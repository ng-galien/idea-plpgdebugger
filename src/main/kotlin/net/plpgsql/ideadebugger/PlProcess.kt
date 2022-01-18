/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.database.util.match
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.concurrency.runAsync
import kotlin.properties.Delegates

private const val STEP_TIMEOUT = 300
private const val RESUME_TIMEOUT = 1000
private const val ABORT_TIMEOUT = 100

class PlProcess(
    private val ctrl: PlController
) : SqlDebugProcess(ctrl.xSession) {

    private val logger = getLogger<PlProcess>()
    //private val breakPointHandler = BreakPointHandler()
    private val context = XContext()
    private val stack = XStack(session)
    //private val breakpointManager = XDebuggerManager.getInstance(ctrl.project).breakpointManager

    private var step: PlStep? = null
    private var aborted: Boolean = false
    var debugPort: Int by Delegates.notNull()
    var sessionId: Int by Delegates.notNull()

    val connection: DatabaseConnection by lazy {
        ctrl.dbgConnection
    }
    private val entryPoint: Long by lazy {
        ctrl.entryPoint
    }

    val breakPointCache = mutableListOf<XLineBreakpoint<PlLineBreakpointProperties>>()

    init {
        //breakpointManager.addBreakpointListener(PlLineBreakpointType(), breakPointHandler)
    }

    override fun sessionInitialized() {
        super.sessionInitialized()
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
        goToStep(SQLQuery.STEP_CONTINUE, RESUME_TIMEOUT)
    }

    fun startDebug(port: Int) {
        debugPort = port
        sessionId = plAttach(this) ?: 0
        assert(sessionId != 0)
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

    override fun stop() {
        connection.runCatching {
            if (!connection.remoteConnection.isClosed && !aborted) {
                abort().blockingGet(ABORT_TIMEOUT)
            }
        }
        super.stop()
    }

    private fun goToStep(SQLQuery: SQLQuery, timeOut: Int = STEP_TIMEOUT) {
        connection.runCatching {
            step = fetchStep(SQLQuery).blockingGet(timeOut)
            handleStackStatus(stack.updateRemote(step))
        }.onFailure {
            aborted = true
            session.stop()
        }
    }

    private fun fetchStep(SQLQuery: SQLQuery) = runAsync {
        plRunStep(this, SQLQuery)
    }

    private fun abort() = runAsync {
        plAbort(this)
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return PlEditorProvider
    }

    override fun checkCanInitBreakpoints(): Boolean {
        return entryPoint != 0L
    }

    override fun checkCanPerformCommands(): Boolean {
        return entryPoint != 0L
    }

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

        val urlRegex = Regex("^$PL_PROTOCOL_PREFIX(\\d+)")

        override fun registerBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
            getFile(breakpoint)?.let {
                runInEdt { session?.consoleView?.print( "registerBreakpoint: ${it.name} => ${breakpoint.line}\n", ConsoleViewContentType.LOG_INFO_OUTPUT) }
                it.addSourceBreakpoint(breakpoint.line)
                session.setBreakpointVerified(breakpoint)
            }
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>, temporary: Boolean) {
            getFile(breakpoint)?.let {
                runInEdt { session?.consoleView?.print( "unregisterBreakpoint: ${it.name} => ${breakpoint.line}\n", ConsoleViewContentType.LOG_INFO_OUTPUT) }
                it.removeSourceBreakpoint(breakpoint.line)
            }
        }

        private fun getFile(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>): PlFile? {
            val res: PlFile? = (breakpoint.properties?.file) as PlFile?
            if (res != null) return res
            val path = breakpoint.fileUrl.match(urlRegex)?.groups?.last()?.value ?: ""
            return PlVFS.getInstance().findFileByPath(path) as PlFile?
        }

    }


}