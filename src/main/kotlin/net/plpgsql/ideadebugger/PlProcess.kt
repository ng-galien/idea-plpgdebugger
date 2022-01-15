/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.database.debugger.SqlDebuggerEditorsProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.sql.psi.SqlPsiFacade
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.concurrency.runAsync


class PlProcess(
    session: XDebugSession,
    val connection: DatabaseConnection,
    private val entryPoint: Long
) : SqlDebugProcess(session) {

    private val logger = getLogger<PlProcess>()
    private val editor = EditorProvider()
    private val context = XContext()
    var sessionId: Int = 0
    private val stack = XStack(session)
    var step: PlStep? = null
    var aborted: Boolean = false

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
        goToStep(Request.STEP_CONTINUE, 500)
    }

    override fun sessionInitialized() {
        super.sessionInitialized()
        if (entryPoint == 0L) {
            session.stop()
        }
    }

    fun startDebug(port: Int) {
        sessionId = plAttach(connection, port) ?: 0
        stack.update(step)
        session.positionReached(context)
    }

    override fun stop() {
        super.stop()
        try {
            if (!connection.remoteConnection.isClosed && !aborted) {
                abort().blockingGet(100)
            }
        } catch (e: Exception) {
            logger.debug(e)
        } finally {
            connection.remoteConnection.close()
        }
    }

    private fun goToStep(request: Request, timeOut: Int = 300) {
        try {
            step = fetchStep(request).blockingGet(timeOut)
            stack.update(step)
            session.positionReached(context)
        } catch (e: Exception) {
            aborted = true
            session.stop()
        }
    }

    private fun fetchStep(request: Request) = runAsync {
        plRunStep(sessionId, connection, request)
    }

    private fun abort() = runAsync {
        plAbort(connection, sessionId)
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