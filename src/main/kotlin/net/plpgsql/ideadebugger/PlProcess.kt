/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlDebugProcess
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.xdebugger.XDebuggerManager
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
    private val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }

    private val scope = CoroutineScope(Dispatchers.EDT + handler)

    init {
        val manager = XDebuggerManager.getInstance(session.project).breakpointManager

    }

    override fun startStepOver(context: XSuspendContext?) {
        executor.setInfo("User request: startStepOver")
        scope.launch() { goToStep(ApiQuery.STEP_OVER) }

    }

    override fun startStepInto(context: XSuspendContext?) {
        executor.setInfo("User request: startStepInto")
        scope.launch() { goToStep(ApiQuery.STEP_INTO) }
    }

    override fun resume(context: XSuspendContext?) {
        executor.setInfo("User request: resume")
        scope.launch() { goToStep(ApiQuery.STEP_CONTINUE) }
    }

    fun startDebug() {
        executor.setInfo("From auxiliary request: startDebug")
        updateStack(true)
    }

    override fun startForceStepInto(context: XSuspendContext?) {
        executor.setInfo("User request mot supported: use startStepInto")
        scope.launch() { goToStep(ApiQuery.STEP_INTO) }

    }

    override fun startStepOut(context: XSuspendContext?) {
        executor.setInfo("User request mot supported: use resume")
        scope.launch() { goToStep(ApiQuery.STEP_CONTINUE) }
    }

    override fun stop() {
        executor.setInfo("User request: stop")
        if (!executor.interrupted) {
            executor.abort()
        }
    }

    private fun updateStack(first: Boolean = false) {
        stack.clear()
        val stacks = executor.getStack()
        stacks.forEach {
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

            val next = breakpoints["${frame.frame.oid}"]?.isNotEmpty() ?: false

            if (next) {
                executor.getBreakPoints().forEach {
                    executor.updateBreakPoint(ApiQuery.DROP_BREAKPOINT, it.oid, it.line)
                }
            }

            breakpoints["${frame.frame.oid}"]?.forEach {
                addBreakpoint(frame.file, it)
            }

            breakpoints.remove("${frame.frame.oid}")

            if (next) {
                scope.launch() { goToStep(ApiQuery.STEP_CONTINUE) }
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


    private suspend fun goToStep(cmd: ApiQuery) = coroutineScope {
        val step = withTimeout(1000) {
            executor.runStep(cmd) ?: fakeStep
        }
        if (step.oid < 0L) {
            return@coroutineScope
        }
        updateStack()
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
                } else {
                    breakpoints[path]?.let {
                        it.add(breakpoint)
                    } ?: kotlin.run {
                        breakpoints[path] = mutableListOf(breakpoint)
                    }
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
            }

        }

    }


}