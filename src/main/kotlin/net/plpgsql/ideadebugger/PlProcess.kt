/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import java.util.concurrent.ConcurrentLinkedQueue

class PlProcess(
    var controller: PlController
) : SqlDebugProcess(controller.xSession) {

    private val breakpoints = mutableMapOf<String, MutableList<XLineBreakpoint<PlLineBreakpointProperties>>>()
    private val context = XContext()
    val executor = controller.executor
    private var stack: XStack = XStack(this)
    private var breakPointHandler = BreakPointHandler()
    internal val command = ConcurrentLinkedQueue<ApiQuery>()
    val mode: DebugMode = controller.mode
    private val proxyTask = ProxyTask(controller.project, "PL/pg Debug", mode == DebugMode.DIRECT)
    private var proxyProgress: BackgroundableProcessIndicator? = null

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

    fun startDebug() {
        console("Process startDebug")
        executor.setInfo("From auxiliary request: startDebug")
        proxyProgress = BackgroundableProcessIndicator(proxyTask)
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
        if (executor.waitingForCompletion) {
            executor.cancelConnection()
        } else {
            proxyProgress?.cancel()
        }
    }

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        command.add(ApiQuery.VOID)
    }

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
            var existing = PlVirtualFileSystem.getInstance().findFileByPath("${it.oid}")
            val reload = (existing == null) || (existing.md5 != it.md5)
            if (reload) {
                controller.closeFile(existing)
                val def = executor.getFunctionDef(it.oid)
                existing = PlVirtualFileSystem.getInstance().registerNewDefinition(
                    PlFunctionSource(session.project, def)
                )
            } else {
                controller.checkFile(existing)
            }
            if (existing != null) {
                stack.append(it, existing)
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
    fun readyToAcceptBreakPoint(): Boolean = proxyTask.ready && !executor.waitingForCompletion

    inner class ProxyTask(project: Project, title: String, canBeCanceled: Boolean) : Task.Backgroundable(project, title, canBeCanceled) {

        var ready = false
        override fun onCancel() {
            console("ProxyTask canceled")
            executor.cancelAndCloseConnection()
            if (mode == DebugMode.INDIRECT) {
                session.stop()
            }
        }

        override fun onThrowable(error: Throwable) {
            console("Proxy task throws", error)
            executor.cancelAndCloseConnection()
        }

        override fun run(indicator: ProgressIndicator) {

            if (executor.entryPoint == 0L) {
                return
            }
            indicator.isIndeterminate = false
            kotlin.runCatching {
                executor.createListener()
                executor.setGlobalBreakPoint()
                executor.waitForTarget()
            }.onFailure {
                console("Run failed to start", it)
                indicator.cancel()
                return
            }
            ready = true
            do {
                val query = command.poll()
                if (query != null) {
                    var step: PlApiStep? = null
                    when (query) {
                        ApiQuery.VOID -> {
                            step = PlApiStep(executor.entryPoint, -1, "")
                        }
                        ApiQuery.STEP_OVER,
                        ApiQuery.STEP_CONTINUE,
                        ApiQuery.STEP_INTO -> {
                            step = executor.runStep(query)
                            indicator.text2 = "Last step ${query.name}"
                        }
                        else -> {
                            indicator.cancel()
                        }
                    }
                    if (step != null) {
                        val info = updateStack()
                        indicator.fraction = info.ratio
                        if (step.line < 0) {
                            indicator.text2 = "Waiting for step [${info.pos} / ${info.total}]"
                        } else {
                            indicator.text2 = "Last step ${query.name} [${info.pos} / ${info.total}]"
                        }
                        executor.displayInfo()
                    }
                } else {
                    Thread.sleep(200)
                }
                indicator.checkCanceled()
            } while (executor.ready())
            ready = false
            executor.closeConnection()
            console("ProxyTask run end")
            executor.interrupted()
        }

    }

    data class StepInfo(val pos: Int, val total: Int, val ratio: Double)


}