/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.database.debugger.SqlDebuggerEditorsProvider
import com.intellij.database.debugger.SqlLineBreakpointProperties
import com.intellij.database.debugger.SqlLineBreakpointType
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.sql.psi.SqlPsiFacade
import com.intellij.util.MessageBusUtil
import com.intellij.util.messages.MessageBus
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import com.jetbrains.rd.util.string.PrettyPrinter
import com.jetbrains.rd.util.string.print
import org.jetbrains.concurrency.runAsync
import kotlin.properties.Delegates

private const val STEP_TIMEOUT = 300
private const val RESUME_TIMEOUT = 1000
private const val ABORT_TIMEOUT = 100

class PlProcess(
    session: XDebugSession,
    val connection: DatabaseConnection,

) : SqlDebugProcess(session) {

    private val logger = getLogger<PlProcess>()
    private val editor = EditorProvider()
    private val context = XContext()
    var sessionId: Int = 0
    private val stack = XStack(session)
    private var step: PlStep? = null
    private var aborted: Boolean = false
    var entryPoint by Delegates.notNull<Long>()
    var debugPort: Int by Delegates.notNull<Int>()


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
        return editor
    }

    override fun checkCanInitBreakpoints(): Boolean {
        return entryPoint != 0L
    }

    override fun checkCanPerformCommands(): Boolean {
        return entryPoint != 0L
    }

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> {
        return arrayOf(BreakPointHandler())
    }

    inner class EditorProvider : SqlDebuggerEditorsProvider() {
        override fun createExpressionCodeFragment(
            project: Project,
            text: String,
            context: PsiElement?,
            isPhysical: Boolean
        ): PsiFile {
            return SqlPsiFacade.getInstance(project)
                .createExpressionFragment(getPlLanguage(), null, null, text)
        }
    }

    inner class BreakPointHandler :
        XBreakpointHandler<XLineBreakpoint<LineBreakpointProperties>>(LineBreakpointType::class.java) {
        override fun registerBreakpoint(breakpoint: XLineBreakpoint<LineBreakpointProperties>) {

        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<LineBreakpointProperties>, temporary: Boolean) {

        }
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


}