/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.database.debugger.SqlDebuggerEditorsProvider
import com.intellij.database.debugger.SqlLineBreakpointProperties
import com.intellij.database.debugger.SqlLineBreakpointType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.sql.psi.SqlPsiFacade
import com.intellij.util.PlatformIcons
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
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
    private val breakPointHandler = BreakPointHandler()
    private val context = XContext()
    private val stack = XStack(session)
    private val breakpointManager = XDebuggerManager.getInstance(ctrl.project).breakpointManager

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

    init {
        //breakpointManager.addBreakpointListener(PlLineBreakpointType(), breakPointHandler)
    }

    override fun sessionInitialized() {
        super.sessionInitialized()
    }

    override fun startStepOver(context: XSuspendContext?) {
        logger.debug("startStepOver")
        goToStep(Request.STEP_OVER)

    }

    override fun startStepInto(context: XSuspendContext?) {
        logger.debug("startStepInto")
        goToStep(Request.STEP_INTO)
    }

    override fun resume(context: XSuspendContext?) {
        logger.debug("resume")
        goToStep(Request.STEP_CONTINUE, RESUME_TIMEOUT)
    }

    fun startDebug(port: Int) {
        debugPort = port
        sessionId = plAttach(this) ?: 0
        stack.update(step)
        session.positionReached(context)
        runReadAction {
            //session.initBreakpoints()
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

    private fun goToStep(request: Request, timeOut: Int = STEP_TIMEOUT) {
        connection.runCatching {
            step = fetchStep(request).blockingGet(timeOut)
            stack.update(step)
            session.positionReached(context)
        }.onFailure {
            aborted = true
            session.stop()
        }
    }

    private fun fetchStep(request: Request) = runAsync {
        plRunStep(this, request)
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
           // SqlHandler()
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

    inner class LBP: SqlLineBreakpointProperties() {}
    inner class LBT() : SqlLineBreakpointType<SqlLineBreakpointProperties>("test", "test") {
        override fun createBreakpointProperties(file: VirtualFile, line: Int): SqlLineBreakpointProperties? {
            return LBP()
        }
    }


    inner class BreakPointHandler :
        XBreakpointHandler<XLineBreakpoint<PlLineBreakpointProperties>>(PlLineBreakpointType::class.java),
        XBreakpointListener<XLineBreakpoint<PlLineBreakpointProperties>> {


        override fun registerBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
            //stack.addBreakPoint(breakpoint.shortFilePath, breakpoint.line)

        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>, temporary: Boolean) {
           //stack.deleteBreakPoint(breakpoint.shortFilePath, breakpoint.line)

        }

    }


}