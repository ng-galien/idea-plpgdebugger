/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.run

import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
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
import net.plpgsql.ideadebugger.service.PlProcessWatcher
import net.plpgsql.ideadebugger.vfs.PlFunctionSource
import net.plpgsql.ideadebugger.vfs.PlVirtualFileSystem
import java.util.concurrent.LinkedBlockingQueue

class PlProcess(
    session: XDebugSession,
    val executor: PlExecutor
) : SqlDebugProcess(session) {

    private val breakpoints = mutableMapOf<String, MutableList<XLineBreakpoint<PlLineBreakpointProperties>>>()
    private val context = XContext()
    private var stack: XStack = XStack(this)
    private var breakPointHandler = BreakPointHandler()
    internal val command = LinkedBlockingQueue<ApiQuery>()
    var mode: DebugMode = DebugMode.NONE
    private val proxyTask = ProxyTask(session.project, "PL/pg Debug")
    private var proxyProgress: ProxyIndicator? = null
    private val fileManager = PlSourceManager(session.project, executor)
    private var callDef: CallDefinition? = null

    override fun startStepOver(context: XSuspendContext?) {
        console("User request: startStepOver")
        command.add(ApiQuery.STEP_OVER)
    }

    override fun startStepInto(context: XSuspendContext?) {
        console("User request: startStepInto")
        command.add(ApiQuery.STEP_INTO)
    }

    override fun resume(context: XSuspendContext?) {
        console("User request: resume")
        command.add(ApiQuery.STEP_CONTINUE)
    }

    fun startDebug(call : CallDefinition) {
        console("Process startDebug")
        this.callDef = call
        executor.entryPoint = callDef!!.oid
        mode = callDef!!.debugMode
        executor.setInfo("From auxiliary request: startDebug")
        proxyProgress = ProxyIndicator(proxyTask)
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(proxyTask, proxyProgress!!)
        command.add(ApiQuery.VOID)
    }

    override fun startForceStepInto(context: XSuspendContext?) {
        console("User request mot supported: use startStepInto")
        command.add(ApiQuery.STEP_INTO)

    }

    override fun startStepOut(context: XSuspendContext?) {
        console("User request mot supported: use resume")
        command.add(ApiQuery.STEP_OVER)
    }

    override fun stop() {
        console("Process stop")
        proxyProgress?.cancel()
    }

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        command.add(ApiQuery.VOID)
    }

    /**
     *
     */
    fun updateStack(): StepInfo {

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
        return (stack.topFrame as XStack.XFrame).let { frame ->

            // Get stack and file breakpoint list for merge
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

    fun addBreakpoint(file: PlFunctionSource, breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
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
        return true
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
    fun readyToAcceptBreakPoint(): Boolean = proxyTask.running && !executor.waitingForCompletion

    inner class ProxyTask(project: Project, title: String) : Task.Backgroundable(project, title, true) {

        var running = false
        private var watcher = ApplicationManager.getApplication().getService(PlProcessWatcher::class.java)
        private val innerThread = InnerThread()

        override fun run(indicator: ProgressIndicator) {

            indicator.text = "PL/pg Debug (${callDef?.routine})"
            indicator.isIndeterminate = false
            running = true
            innerThread.start()
            watcher.processStarted(this@PlProcess)
            do {
                Thread.sleep(100)
            } while (!indicator.isCanceled)
            running = false
            innerThread.cancel()
            console("ProxyTask run end")
            watcher.processFinished(this@PlProcess)
            if (mode == DebugMode.INDIRECT) {
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

    inner class InnerThread(): Thread() {

        fun cancel() {
            executor.cancelStatement()
            if (!command.isEmpty()) {
                command.clear()
            }
            command.add(ApiQuery.ABORT)
        }

        override fun run() {

            if (executor.entryPoint == 0L) {
                return
            }

            kotlin.runCatching {
                executor.createListener()
                executor.setGlobalBreakPoint()
                executor.waitForTarget()
            }.onFailure {
                console("Run failed to start", it)
                proxyProgress?.cancel()
            }

            if (executor.interrupted()) {
                return
            }

            while (proxyTask.running) {
                val query = command.take()
                var step: PlApiStep? = null
                when (query) {
                    ApiQuery.ABORT -> {
                        executor.abort()
                    }
                    ApiQuery.VOID -> {
                        step = PlApiStep(executor.entryPoint, -1, "")
                    }
                    ApiQuery.STEP_OVER,
                    ApiQuery.STEP_CONTINUE,
                    ApiQuery.STEP_INTO -> {
                        step = executor.runStep(query)
                    }
                    else -> {
                        proxyProgress?.cancel()
                    }
                }

                if (step != null) {
                    val info = updateStack()
                    proxyProgress?.displayInfo(query, step, info)
                    executor.printStack()
                }
            }
            executor.cancelAndCloseConnection()
        }

    }

    data class StepInfo(val pos: Int, val total: Int, val ratio: Double)


}