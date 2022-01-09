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
import com.intellij.psi.PsiFileFactory
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext


class Process(session: XDebugSession, private val connectionPoint: DatabaseConnectionPoint) : SqlDebugProcess(session) {

    private val editor = EditorProvider()

    override fun startStepOver(context: XSuspendContext?) {
        //logger.debug { "startStepOver" }
        //stepOver(connection, sessionId)
    }

    override fun startStepInto(context: XSuspendContext?) {
        //logger.debug { "startStepInto" }
        //stepInto(connection, sessionId)
    }

    override fun sessionInitialized() {
        super.sessionInitialized()
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return editor
    }

    override fun checkCanInitBreakpoints(): Boolean {
        return true
    }

    override fun checkCanPerformCommands(): Boolean {
        return true
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
            return PsiFileFactory.getInstance(project).createFileFromText("file.dummy", fileType, text, 0, isPhysical)
        }
    }

    inner class BreakPointHandler :
        XBreakpointHandler<XLineBreakpoint<LineBreakpointProperties>>(LineBreakpointType::class.java) {
        override fun registerBreakpoint(breakpoint: XLineBreakpoint<LineBreakpointProperties>) {

        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<LineBreakpointProperties>, temporary: Boolean) {

        }

    }


}