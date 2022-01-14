/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.debugger

import com.intellij.database.dataSource.DatabaseConnectionPoint
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
import kotlin.properties.Delegates


class PlProcess(
    session: XDebugSession,
    private val connectionPoint: DatabaseConnectionPoint,
    private val entryPoint: Long
) : SqlDebugProcess(session) {

    private val logger = getLogger<PlProcess>()
    private val editor = EditorProvider()
    private val context = XContext()
    val connection = getConnection(session.project, connectionPoint)
    var sessionId: Int = 0

    private val stack = XStack(session)

    override fun startStepOver(context: XSuspendContext?) {
        logger.debug("startStepOver")
        plStepOver(connection, sessionId)
        reached()
    }

    override fun startStepInto(context: XSuspendContext?) {
        logger.debug("startStepInto")
        plStepInto(connection, sessionId)
        reached()
    }

    override fun resume(context: XSuspendContext?) {
        logger.debug("resume")
        plContinue(connection, sessionId)
    }

    override fun sessionInitialized() {
        super.sessionInitialized()
        if (entryPoint == 0L) {
            connection.remoteConnection.close()
            stop()
        }
    }

    fun startDebug(port: Int) {
        sessionId = plAttach(connection, port) ?: 0
        reached()
    }

    override fun stop() {
        super.stop()
        connection.remoteConnection.close()
    }

    private fun reached() {
        stack.update()
        session.positionReached(context)
    }

    fun getStack() = plGetStack(connection, sessionId)

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