package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.database.debugger.SqlDebuggerFacade
import com.intellij.database.model.basic.BasicSourceAware
import com.intellij.database.util.SearchPath
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.sql.SqlFileType
import com.intellij.sql.psi.SqlStatement
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import net.plpgsql.ideadebugger.breakpoint.DBBreakpointProperties
import net.plpgsql.ideadebugger.breakpoint.DBLineBreakpointType

class PluginFacade: SqlDebuggerFacade {

    var selected: PsiElement? = null
    override fun isApplicableToDebugStatement(statement: SqlStatement) = statementOk(statement)?.let {
        selected = it
        true
    }?: false

    override fun isApplicableToDebugRoutine(basicSourceAware: BasicSourceAware) = true

    override fun canDebug(datasource: LocalDataSource) = datasource.dbms.isPostgres

    override fun createController(
        project: Project,
        connectionPoint: DatabaseConnectionPoint,
        request: DataRequest.OwnerEx,
        p3: Boolean,
        file: VirtualFile?,
        range: RangeMarker?, searchPath: SearchPath?
    ): SqlDebugController {
        debugger().registerConnectionPoint(project, connectionPoint, searchPath)
        return selected?.let {
            findProcedure(it)?.let { oid ->
                PluginController(project, oid)
            }
        }?: let {
            notifyError(project, "Cannot find procedure")
            throw RuntimeException("Cannot find procedure")
        }
    }

}

class PluginController(private val project: Project, private val oid: PostgresLib.Oid): SqlDebugController() {

    companion object {
        private val logger = getLogger(PluginController::class)
    }

    override fun getReady() {
        logger.debug("User request: getReady")
    }

    override fun initLocal(dbgSession: XDebugSession): XDebugProcess {
        logger.debug("User request: initLocal")
        return runInternal {
            val listener = PostgresLib.DebugSession(it.createListener())
            it.setGlobalBreakpoint(listener, oid)
            PluginProcess(dbgSession, listener)
        }?: let {
            notifyError(project, "Cannot create debug process")
            throw RuntimeException("Cannot create debug process")
        }
    }

    override fun initRemote(connection: DatabaseConnection) {
        logger.debug("User request: initRemote")
    }

    override fun debugBegin() {
        logger.debug("User request: debugBegin")
    }

    override fun debugEnd() {
        logger.debug("User request: debugEnd")
    }

    override fun close() {
        logger.debug("User request: close")
    }


}

class PluginProcess(val xSession: XDebugSession, val debugSession: PostgresLib.DebugSession)
    : SqlDebugProcess(xSession) {

        var started = false
        private val xContext = PluginContext(debugSession)

    init {
        startSession(debugSession) {
            started = true
            refreshStack(xSession.project, debugSession, xContext.stack)
            xSession.positionReached(xContext)
        }
    }
    companion object {
        private val logger = getLogger(PluginProcess::class)
    }
    override fun getEditorsProvider(): XDebuggerEditorsProvider = PluginEditorProvider.INSTANCE

    override fun startStepOver(context: XSuspendContext?) {
        logger.debug("User request: startStepOver")
        runInternalAsync(
            { it.stepOver(debugSession) },
            this::handleSteps
        )
    }

    override fun startStepInto(context: XSuspendContext?) {
        logger.debug("User request: startStepInto")
        runInternalAsync(
            { it.stepInto(debugSession) },
            this::handleSteps
        )
    }

    override fun resume(context: XSuspendContext?) {
        logger.debug("User request: resume")
        runInternalAsync(
            { it.continueExecution(debugSession) },
            this::handleSteps
        )
    }

    private fun handleSteps(steps: List<PostgresLib.Step>) {
        runInternalAsync(
            { it.getStack(debugSession) },
            this::handleStack
        )
    }

    private fun handleStack(frames: List<PostgresLib.Frame>) {
        refreshStack(xSession.project, debugSession, xContext.stack)
        xSession.positionReached(xContext)
    }

    override fun checkCanPerformCommands() = started

    override fun checkCanInitBreakpoints() = true
}

class PluginContext(session: PostgresLib.DebugSession) : XSuspendContext() {
    val stack = PluginStack(session)
    override fun getActiveExecutionStack(): XExecutionStack = stack
    override fun getExecutionStacks(): Array<XExecutionStack> = arrayOf(stack)
    override fun computeExecutionStacks(container: XExecutionStackContainer?) {
        container?.addExecutionStack(mutableListOf(stack), true)
    }
}


class BreakPointHandler() :
    XBreakpointHandler<XLineBreakpoint<DBBreakpointProperties>>(DBLineBreakpointType::class.java),
    XBreakpointListener<XLineBreakpoint<DBBreakpointProperties>> {

    private val breakpoints: MutableList<XLineBreakpoint<DBBreakpointProperties>> = mutableListOf()

    override fun registerBreakpoint(breakpoint: XLineBreakpoint<DBBreakpointProperties>) {
        breakpoints.add(breakpoint)
    }

    override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<DBBreakpointProperties>, temporary: Boolean) {
        breakpoints.remove(breakpoint)
    }

}

class PluginEditorProvider : XDebuggerEditorsProvider() {

    companion object {
        val INSTANCE = PluginEditorProvider()
    }

    override fun getFileType(): FileType {
        return SqlFileType.INSTANCE
    }

    override fun createDocument(
        project: Project,
        expression: XExpression,
        sourcePosition: XSourcePosition?,
        mode: EvaluationMode
    ): Document {
        return EditorFactory.getInstance().createDocument("")
    }
}