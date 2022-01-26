/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.openapi.application.runReadAction
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.*

class PlProcess(
    var controller: PlController
) : SqlDebugProcess(controller.xSession) {

    private val breakpoints = mutableMapOf<String, MutableList<XLineBreakpoint<PlLineBreakpointProperties>>>()
    private val context = XContext()
    private val fakeStep = PlApiStep(-1, 0, "")
    private val executor = controller.executor
    private var stack: XStack = XStack(this)
    private var breakPointHandler = BreakPointHandler()


    init {

    }

    override fun startStepOver(context: XSuspendContext?) {
        executor.setInfo("User request: startStepOver")
        controller.scope.launch() { goToStep(ApiQuery.STEP_OVER) }
    }

    override fun startStepInto(context: XSuspendContext?) {
        executor.setInfo("User request: startStepInto")
        controller.scope.launch() { goToStep(ApiQuery.STEP_INTO) }
    }

    override fun resume(context: XSuspendContext?) {
        executor.setInfo("User request: resume")
        controller.scope.launch() { goToStep(ApiQuery.STEP_CONTINUE) }
    }

    fun startDebug() {
        executor.setInfo("From auxiliary request: startDebug")
        updateStack()
    }

    override fun startForceStepInto(context: XSuspendContext?) {
        executor.setInfo("User request mot supported: use startStepInto")
        controller.scope.launch() { goToStep(ApiQuery.STEP_INTO) }

    }

    override fun startStepOut(context: XSuspendContext?) {
        executor.setInfo("User request mot supported: use resume")
        controller.scope.launch() { goToStep(ApiQuery.STEP_CONTINUE) }
    }

    override fun stop() {
        executor.setInfo("User request: stop")
        runCatching {
            controller.scope.cancel("Stopped by user")
        }
        executor.abort()
    }

    private fun updateStack() {

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
        (stack.topFrame as XStack.XFrame)?.let { frame ->

            // Get stack and file breakpoint list for merge
            val stackBreakPoints = executor.getBreakPoints().filter {
                it.oid == frame.file.oid
            }.map {
                it.line + frame.file.start
            }
            val fileBreakPoints = breakpoints["${frame.frame.oid}"]?.map {
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

            breakpoints["${frame.frame.oid}"]?.filter {
                toAdd.contains(it.line)
            }?.forEach {
               addBreakpoint(frame.file, it)
            }
            if (first) {
                breakpoints["${frame.frame.oid}"]?.filter {
                    toCheck.contains(it.line)
                }?.forEach {
                    session.setBreakpointVerified(it)
                }
            }

            //We can go to next step
            val next = first && fileBreakPoints.isNotEmpty()
            if (next) {
                executor.setDebug("Got to next")
                controller.scope.launch() { goToStep(ApiQuery.STEP_CONTINUE) }
            } else {
                session.positionReached(context)
            }
        }

    }

    fun addBreakpoint(file: PlFunctionSource, breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
        if (executor.updateBreakPoint(
                ApiQuery.ADD_BREAKPOINT,
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


    private suspend fun goToStep(cmd: ApiQuery) {
        kotlin.runCatching {
            withTimeout(controller.settings.stepTimeOut.toLong()) {
                executor.runStep(cmd)
            }
            updateStack()
            executor.displayInfo()
        }.onFailure {
            executor.setError("Command Timeout ", it)
            session.stop()
        }

    }

    private suspend fun abort() {
        kotlin.runCatching {
            withTimeout(controller.settings.stepTimeOut.toLong()) {
                executor.abort()
            }
        }.onFailure {
            executor.setError("Command Timeout ", it)
        }
        executor.displayInfo()
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
                breakpoints[path]?.let {
                    it.add(breakpoint)
                } ?: kotlin.run {
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

}