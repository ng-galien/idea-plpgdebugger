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
import com.intellij.xdebugger.breakpoints.*
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
    private val proxyTask = ProxyTask(controller.project, "PL/pg Debug")
    val proxyProgress = ProxyProgress(proxyTask)

    override fun startStepOver(context: XSuspendContext?) {
        executor.setInfo("User request: startStepOver")
        command.add(ApiQuery.STEP_OVER)
    }

    override fun startStepInto(context: XSuspendContext?) {
        executor.setInfo("User request: startStepInto")
        command.add(ApiQuery.STEP_INTO)
    }

    override fun resume(context: XSuspendContext?) {
        executor.setInfo("User request: resume")
        command.add(ApiQuery.STEP_CONTINUE)
    }

    fun startDebug() {
        executor.setInfo("From auxiliary request: startDebug")
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(proxyTask, proxyProgress)
        command.add(ApiQuery.VOID)
    }

    override fun startForceStepInto(context: XSuspendContext?) {
        executor.setInfo("User request mot supported: use startStepInto")
        command.add(ApiQuery.STEP_INTO)

    }

    override fun startStepOut(context: XSuspendContext?) {
        executor.setInfo("User request mot supported: use resume")
        command.add(ApiQuery.STEP_OVER)
    }

    override fun stop() {
        executor.terminateBackEnd()
        proxyTask.running = false
    }

    fun updateStack(): Double {

        val plStacks = executor.getStack()
        // If we reach this frame for the first time
        val first = stack.topFrame?.let {
            (it as XStack.XFrame).file.oid != plStacks.firstOrNull()?.oid
        } ?: true
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
            frame.getSourceRatio()
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


    inner class BreakPointHandler() :
        XBreakpointHandler<XLineBreakpoint<PlLineBreakpointProperties>>(PlLineBreakpointType::class.java),
        XBreakpointListener<XLineBreakpoint<PlLineBreakpointProperties>> {

        override fun registerBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
            executor.setInfo("registerBreakpoint: ${breakpoint.fileUrl} => ${breakpoint.line}")
            runReadAction {
                val path = breakpoint.fileUrl.removePrefix(PlVirtualFileSystem.PROTOCOL_PREFIX)
                val file = PlVirtualFileSystem.getInstance().findFileByPath(path)
                if (file != null && executor.ready()) {
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
                if (file != null && executor.ready()) {
                    dropBreakpoint(file, breakpoint)
                }
                breakpoints[path]?.removeIf {
                    it.line == breakpoint.line
                }
            }
        }
    }

    inner class ProxyTask(project: Project, title: String) : Task.Backgroundable(project, title, true) {
        var running = true

        override fun onCancel() {
            //println("canceled")
            executor.abort()
            executor.terminateBackEnd()
            running = false
        }

        override fun run(indicator: ProgressIndicator) {

            if (executor.entryPoint == 0L) {
                return
            }
            indicator.isIndeterminate = false
            executor.setGlobalBreakPoint()
            executor.waitForTarget()

            do {
                indicator.checkCanceled()
                val query = command.poll()
                if (query != null) {
                    var step: PlApiStep? = null
                    when (query) {
                        ApiQuery.VOID -> {
                            step = PlApiStep(executor.entryPoint, -1, "")
                            indicator.text2 = "Waiting for debug command"
                        }
                        ApiQuery.STEP_OVER,
                        ApiQuery.STEP_CONTINUE,
                        ApiQuery.STEP_INTO -> {
                            step = executor.runStep(query)
                            indicator.text2 = "Last step ${query.name}"
                        }
                        else -> {
                            //TODO manage it
                        }
                    }
                    if (step != null) {
                        indicator.fraction = updateStack()
                        indicator.text2 = "Last step ${query.name}"
                        executor.displayInfo()
                    }
                } else {
                    Thread.sleep(200)
                }
            } while (!indicator.isCanceled && running)

        }

    }

    inner class ProxyProgress(task: ProxyTask) : BackgroundableProcessIndicator(task) {
    }

}