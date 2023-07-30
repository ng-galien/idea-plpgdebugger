/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.run

import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.collections.immutable.toImmutableList
import net.plpgsql.ideadebugger.*
import net.plpgsql.ideadebugger.breakpoint.DBBreakpointProperties
import net.plpgsql.ideadebugger.breakpoint.DBLineBreakpointType
import net.plpgsql.ideadebugger.command.ApiQuery
import net.plpgsql.ideadebugger.command.PlApiStep
import net.plpgsql.ideadebugger.command.PlExecutor
import net.plpgsql.ideadebugger.service.PlProcessWatcher
import net.plpgsql.ideadebugger.vfs.refreshFileFromStackFrame
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SQL runner for the debugger session
 */
class PlProcess(
    session: XDebugSession,
    val executor: PlExecutor
) : SqlDebugProcess(session) {

    private val logger = logger<PlProcess>()
    private val breakpoints: MutableList<XLineBreakpoint<DBBreakpointProperties>> = mutableListOf()
    private val context = XContext()
    private var stack: XStack = XStack(this)
    private var breakPointHandler = BreakPointHandler()
    internal val command = LinkedBlockingQueue<ApiQuery>()
    var mode: DebugMode = DebugMode.NONE
    private val proxyTask = ProxyTask(session.project, "PL/pg Debug")
    private var proxyProgress: ProxyIndicator? = null
    private var callDef: CallDefinition? = null

    //getter for breakpoints
    fun filesBreakpointList() = breakpoints.toImmutableList()

    init {
        executor.xSession = session
    }

    override fun startStepOver(context: XSuspendContext?) {
        logger.debug("User request: startStepOver")
        command.add(ApiQuery.STEP_OVER)
    }

    override fun startStepInto(context: XSuspendContext?) {
        logger.debug("User request: startStepInto")
        command.add(ApiQuery.STEP_INTO)
    }

    override fun resume(context: XSuspendContext?) {
        logger.debug("User request: resume")
        command.add(ApiQuery.STEP_CONTINUE)
    }

    fun startDebug(call : CallDefinition) {
        logger.debug("Process startDebug")
        this.callDef = call
        executor.entryPoint = callDef!!.oid
        mode = callDef!!.debugMode
        executor.setInfo("From auxiliary request: startDebug")
        proxyProgress = ProxyIndicator(proxyTask)
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(proxyTask, proxyProgress!!)
        command.add(ApiQuery.VOID)
    }

    override fun startForceStepInto(context: XSuspendContext?) {
        logger.debug("User request not supported: use startStepInto")
        command.add(ApiQuery.STEP_INTO)
    }

    override fun startStepOut(context: XSuspendContext?) {
        logger.debug("User request not supported: use resume")
        command.add(ApiQuery.STEP_OVER)
    }

    override fun stop() {
        logger.debug("Process stop")
        proxyProgress?.cancel()
    }

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        logger.debug("User request: runToPosition")
        command.add(ApiQuery.VOID)
    }

    /**
     *
     */
    fun updateStack(): StepInfo {
        logger.debug("User request: updateStack")
        val frames = executor.getStack().map {
            val file = refreshFileFromStackFrame(session.project, executor, it)
            val firstTime = !stack.hasFile(it.oid)
            stack.newFrame(file, it.line, it.level, firstTime)
        }
        stack.setFrames(frames)
        val next = needToContinueExecution(stack, this)
        if (next) {
            executor.setDebug("Got to next")
            command.add(ApiQuery.STEP_CONTINUE)
        } else {
            session.positionReached(context)
        }
        return stepInfoForStack(stack)
    }

    override fun getEditorsProvider() = PlEditorProvider.INSTANCE
    override fun checkCanInitBreakpoints() = true
    override fun checkCanPerformCommands() = proxyTask.running.get()
    override fun getBreakpointHandlers() = arrayOf(breakPointHandler)

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


    inner class BreakPointHandler :
        XBreakpointHandler<XLineBreakpoint<DBBreakpointProperties>>(DBLineBreakpointType::class.java),
        XBreakpointListener<XLineBreakpoint<DBBreakpointProperties>> {

        override fun registerBreakpoint(breakpoint: XLineBreakpoint<DBBreakpointProperties>) {
           ensureBreakPoint(this@PlProcess, breakpoint)?.let {
               breakpoints.add(breakpoint)
               if (readyToAcceptBreakPoint()) {
                   addLineBreakPoint(this@PlProcess, breakpoint)
               }
           }
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<DBBreakpointProperties>, temporary: Boolean) {
            ensureBreakPoint(this@PlProcess, breakpoint)?.let {
                breakpoints.remove(breakpoint)
                if (readyToAcceptBreakPoint()) {
                    deleteLineBreakpoint(this@PlProcess, it)
                }
            }
        }
    }

    fun readyToAcceptBreakPoint():
            Boolean = proxyTask.running.get() && !executor.waitingForCompletion

    inner class ProxyTask(project: Project, title: String) : Task.Backgroundable(project, title, true) {

        private val logger1 = logger<ProxyTask>()
        val running = AtomicBoolean(false)

        private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)
        private val innerThread = InnerThread()

        override fun run(indicator: ProgressIndicator) {

            logger1.debug("Run proxy Task")

            indicator.text = "PL/pg Debug (${callDef?.routine})"
            indicator.isIndeterminate = false

            running.set(true)
            innerThread.start()
            watcher.processStarted(this@PlProcess, mode, callDef?.oid ?: 0L)
            do {
                Thread.sleep(100)
            } while (!indicator.isCanceled)
            running.set(false)

            innerThread.cancel()
            logger1.debug("ProxyTask run end")
            watcher.processFinished(this@PlProcess)

            if (mode == DebugMode.INDIRECT) {
                logger1.debug("Stops session for indirect debugging")
                session.stop()
            }
        }

    }

    inner class ProxyIndicator(task: ProxyTask): BackgroundableProcessIndicator(task) {
        fun displayInfo(query: ApiQuery, step: PlApiStep, info: StepInfo) {
            fraction = info.ratio
            if (step.line < 0) {
                proxyProgress?.text2 = "Waiting for step [${info.pos} / ${info.total}]"
            } else {
                proxyProgress?.text2 = "Last step ${query.name} [${info.pos} / ${info.total}]"
            }
        }
    }

    /**
     * Thread for debugging commands
     */
    inner class InnerThread : Thread() {

        private val logger1 = logger<InnerThread>()

        fun cancel() {
            logger1.debug("cancel")
            executor.cancelStatement()
            if (!command.isEmpty()) {
                command.clear()
            }
            command.add(ApiQuery.ABORT)
        }

        override fun run() {

            logger1.debug("Thread run")
            if (executor.entryPoint == 0L) {
                logger1.warn("Invalid entry point")
                return
            }

            kotlin.runCatching {
                executor.createListener()
                executor.setGlobalBreakPoint()
                executor.waitForTarget()
                syncBreakPoints(this@PlProcess)
            }.onFailure {
                logger1.error("Run failed to start", it)
                proxyProgress?.cancel()
            }

            if (executor.interrupted()) {
                logger1.debug("Executor is interrupted")
                return
            }
            //Loop while proxy task is not interrupted
            while (proxyTask.running.get()) {

                val query = command.take()
                logger1.debug("Command was taken from queue: ${query.name}")
                val step: PlApiStep? = getStep(query)
                if (step != null) {
                    updateStack().let {
                        proxyProgress?.displayInfo(query, step, it)
                        executor.printStack()
                    }
                }
            }
            executor.cancelAndCloseConnection()
            logger1.debug("Thread end")
        }

        private fun getStep(query: ApiQuery): PlApiStep? {
            return when (query) {
                ApiQuery.ABORT -> {
                    executor.abort()
                    null
                }
                ApiQuery.VOID -> {
                    PlApiStep(executor.entryPoint, -1, "")
                }
                ApiQuery.STEP_OVER,
                ApiQuery.STEP_CONTINUE,
                ApiQuery.STEP_INTO -> {
                    executor.runStep(query)
                }
                else -> {
                    logger1.warn("Unsupported command, canceling")
                    proxyProgress?.cancel()
                    null
                }
            }
        }

    }

    data class StepInfo(val pos: Int, val total: Int, val ratio: Double)

}