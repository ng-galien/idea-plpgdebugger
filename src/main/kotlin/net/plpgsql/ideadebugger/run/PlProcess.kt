/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.run

import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
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
import net.plpgsql.ideadebugger.*
import net.plpgsql.ideadebugger.breakpoint.PlLineBreakpointProperties
import net.plpgsql.ideadebugger.breakpoint.PlLineBreakpointType
import net.plpgsql.ideadebugger.command.ApiQuery
import net.plpgsql.ideadebugger.command.PlApiStep
import net.plpgsql.ideadebugger.command.PlExecutor
import net.plpgsql.ideadebugger.service.PlProcessWatcher
import net.plpgsql.ideadebugger.vfs.PlFunctionSource
import net.plpgsql.ideadebugger.vfs.PlSourceManager
import net.plpgsql.ideadebugger.vfs.PlVirtualFileSystem
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
    private val breakpoints = mutableMapOf<String, MutableList<XLineBreakpoint<PlLineBreakpointProperties>>>()
    private val context = XContext()
    private var stack: XStack = XStack(this)
    private var breakPointHandler = BreakPointHandler()
    internal val command = LinkedBlockingQueue<ApiQuery>()
    var mode: DebugMode = DebugMode.NONE
    private val proxyTask = ProxyTask(session.project, "PL/pg Debug")
    private var proxyProgress: ProxyIndicator? = null
    val fileManager = PlSourceManager(session.project, executor)
    private var callDef: CallDefinition? = null

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
    fun updateStack(): StepInfo? {

        logger.debug("User request: updateStack")
        val plStacks = executor.getStack()
        // If we reach this frame for the first time
        var first = stack.topFrame?.let {
            (it as XStack.XFrame).file.oid != plStacks.firstOrNull()?.oid
        } ?: true
        // In case of indirect debugging check it's first line
        if (!first && mode == DebugMode.INDIRECT && stack.topFrame != null) {
            (stack.topFrame as XStack.XFrame).let {
                first = it.getSourceLine() == it.file.codeRange.first + 1
            }
        }
        executor.setDebug("Reach frame ${plStacks.firstOrNull()?.oid}, first=$first")
        stack.clear()
        plStacks.forEach {
            fileManager.update(it)?.let {
                source -> stack.append(it, source)
            }
        }
        return (stack.topFrame as XStack.XFrame?)?.let { frame ->

            // Get stack and file breakpoint list for merge
            val fileBreakPoints = mergeBreakPoint(first, frame)

            //We can go to next step
            val next = first && fileBreakPoints.isNotEmpty()
                    && !fileBreakPoints.any { frame.plFrame.line == it - frame.file.start }
            if (next) {
                executor.setDebug("Got to next")
                command.add(ApiQuery.STEP_CONTINUE)
            } else {
                session.positionReached(context)
            }
            StepInfo(frame.getSourceLine() + 1, frame.file.codeRange.second, frame.getSourceRatio())
        }

    }

    private fun mergeBreakPoint(first: Boolean, frame: XStack.XFrame): List<Int> {
        val stackBreakPoints = executor.getBreakPoints().filter {
            it.oid == frame.file.oid
        }.filter {
            it.line > 0
        }.map {
            it.line + frame.file.start
        }
        val fileBreakPoints = breakpoints["${frame.plFrame.oid}"]?.map {
            it.line
        } ?: listOf()

        // Remove stack break point not in file list
        stackBreakPoints.filter {
            !fileBreakPoints.contains(it)
        }.forEach {
            executor.updateBreakPoint(ApiQuery.DROP_BREAKPOINT, frame.file.oid, it - frame.file.start)
        }

        // Add missing breakPoints to stack and verify for existing
        val (toCheck, toAdd) = fileBreakPoints.partition { stackBreakPoints.contains(it) }

        breakpoints["${frame.plFrame.oid}"]?.filter {
            toAdd.contains(it.line)
        }?.forEach {
            addBreakpoint(frame.file, it)
        }
        if (first) {
            breakpoints["${frame.plFrame.oid}"]?.filter {
                toCheck.contains(it.line)
            }?.forEach {
                session.setBreakpointVerified(it)
            }
        }
        return fileBreakPoints
    }

    fun addBreakpoint(file: PlFunctionSource, breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
        logger.debug("addBreakpoint: file=${file.name}, line=${breakpoint.line}")
        if (executor.updateBreakPoint(
                ApiQuery.SET_BREAKPOINT,
                file.oid,
                breakpoint.line - file.start
            )
        ) {
            session.setBreakpointVerified(breakpoint)
        }
    }

    fun dropBreakpoint(file: PlFunctionSource, breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
        logger.debug("dropBreakpoint: file=${file.name}, line=${breakpoint.line}")
        executor.updateBreakPoint(
            ApiQuery.DROP_BREAKPOINT,
            file.oid,
            breakpoint.line - file.start
        )
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return PlEditorProvider.INSTANCE
    }

    override fun checkCanInitBreakpoints(): Boolean {
        return true
    }

    override fun checkCanPerformCommands(): Boolean {
        return proxyTask.running.get()
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


    inner class BreakPointHandler :
        XBreakpointHandler<XLineBreakpoint<PlLineBreakpointProperties>>(PlLineBreakpointType::class.java),
        XBreakpointListener<XLineBreakpoint<PlLineBreakpointProperties>> {

        override fun registerBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
            executor.setInfo("registerBreakpoint: ${breakpoint.fileUrl} => ${breakpoint.line}")
            runReadAction {
                val path = breakpoint.fileUrl.removePrefix(PlVirtualFileSystem.PROTOCOL_PREFIX)
                val file = PlVirtualFileSystem.getInstance().findFileByPath(path)
                if (file != null && readyToAcceptBreakPoint()) {
                    addBreakpoint(file, breakpoint)
                }
                breakpoints[path]?.add(breakpoint) ?: kotlin.run {
                    breakpoints[path] = mutableListOf(breakpoint)
                }
            }
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>, temporary: Boolean) {
            executor.setInfo("unregisterBreakpoint: ${breakpoint.fileUrl} => ${breakpoint.line}")
            runReadAction {
                val path = breakpoint.fileUrl.removePrefix(PlVirtualFileSystem.PROTOCOL_PREFIX)
                val file = PlVirtualFileSystem.getInstance().findFileByPath(path)
                if (file != null && readyToAcceptBreakPoint()) {
                    dropBreakpoint(file, breakpoint)
                }
                breakpoints[path]?.removeIf {
                    it.line == breakpoint.line
                }
            }
        }
    }

    fun readyToAcceptBreakPoint(): Boolean = proxyTask.running.get() && !executor.waitingForCompletion

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
                    updateStack()?.let {
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