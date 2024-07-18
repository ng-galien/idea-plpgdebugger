/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

    /**
     * A logger instance for the PlProcess class.
     */
    private val logger = logger<PlProcess>()
    /**
     * Mutable map that stores breakpoints for different files.
     *
     * Key: String        - The file path.
     * Value: MutableList<XLineBreakpoint<PlLineBreakpointProperties>> - The list of breakpoints for the file.
     */
    private val breakpoints = mutableMapOf<String, MutableList<XLineBreakpoint<PlLineBreakpointProperties>>>()
    /**
     * Holds the execution context for the XDebugger.
     * This context is used to manage the execution stacks and breakpoints.
     */
    private val context = XContext()
    /**
     * Represents a stack data structure for storing elements.
     *
     * @property stack The underlying stack data structure.
     */
    private var stack: XStack = XStack(this)
    /**
     * A variable used to handle breakpoints in the PlProcess class.
     *
     * @property breakPointHandler The instance of the `BreakPointHandler` class used for handling breakpoints.
     *
     * @see PlProcess
     * @see BreakPointHandler
     */
    private var breakPointHandler = BreakPointHandler()
    /**
     *
     */
    internal val command = LinkedBlockingQueue<ApiQuery>()
    /**
     * Represents the debugging mode.
     *
     * It can have the following values:
     * - NONE: No debugging.
     * - DIRECT: Direct debugging, when the function is called directly from the editor.
     * - INDIRECT: Indirect debugging, when the function is called from another function.
     */
    var mode: DebugMode = DebugMode.NONE
    /**
     * Represents a background task for proxying PL/pg Debug operations.
     *
     * @property project The project associated with the task.
     * @property title The title of the task.
     * @property running A boolean indicating whether the task is currently running.
     * @property logger1 The logger for the task.
     * @property watcher The PlProcessWatcher service.
     * @property innerThread The inner thread used by the task.
     */
    private val proxyTask = ProxyTask(session.project, "PL/pg Debug")
    /**
     * Represents the progress indicator used during proxy task execution.
     * The indicator displays information about the current step and progress of the task.
     *
     * @property proxyProgress The instance of [ProxyIndicator] used for displaying the progress information.
     */
    private var proxyProgress: ProxyIndicator? = null
    /**
     * Manages the PL source code for a given project.
     * @param project The project in which the PL source code is managed.
     * @param executor The executor used to interact with the PL database.
     */
    val fileManager = PlSourceManager(session.project, executor)
    /**
     * Represents the call definition for a routine.
     * This variable holds information about the routine call, such as the debug mode, the PSI element, and the query.
     *
     * @property debugMode The debug mode of the routine call.
     * @property psi The PSI element associated with the routine call.
     * @property query The query used for the routine call.
     * @property schema The schema of the routine call.
     * @property routine The name of the routine.
     * @property oid The object ID of the routine.
     * @property args The arguments of the routine call.
     * @property selectionOk Flag indicating if the routine call can be selected.
     *
     * @constructor Creates a CallDefinition object with the given debug mode, PSI element, and query.
     */
    private var callDef: CallDefinition? = null

    init {
        executor.xSession = session
    }

    /**
     * Executes a step over operation in the debugging process.
     *
     * @param context The suspend context of the debugger.
     */
    override fun startStepOver(context: XSuspendContext?) {
        logger.debug("User request: startStepOver")
        command.add(ApiQuery.STEP_OVER)
    }

    /**
     * Starts a step into operation, which allows the debugger to step into the next line of code.
     *
     * @param context The suspend context in which the step into operation is performed.
     */
    override fun startStepInto(context: XSuspendContext?) {
        logger.debug("User request: startStepInto")
        command.add(ApiQuery.STEP_INTO)
    }

    /**
     * Resumes the execution of the program.
     *
     * @param context the suspend context
     */
    override fun resume(context: XSuspendContext?) {
        logger.debug("User request: resume")
        command.add(ApiQuery.STEP_CONTINUE)
    }

    /**
     * Starts the debugging process.
     *
     * @param call the CallDefinition object containing the debug mode, PsiElement, and query
     */
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

    /**
     * Starts a force step into operation, which allows the debugger to step into the next line of code forcefully.
     *
     * @param context The suspend context in which the step into operation is performed.
     */
    override fun startForceStepInto(context: XSuspendContext?) {
        logger.debug("User request not supported: use startStepInto")
        command.add(ApiQuery.STEP_INTO)
    }

    /**
     * Starts a step out operation, which allows the debugger to step out of the current method or function.
     *
     * @param context The suspend context in which the step out operation is performed.
     */
    override fun startStepOut(context: XSuspendContext?) {
        logger.debug("User request not supported: use resume")
        command.add(ApiQuery.STEP_OVER)
    }

    /**
     * Stops the process.
     */
    override fun stop() {
        logger.debug("Process stop")
        proxyProgress?.cancel()
    }

    /**
     * Runs the debugger to the specified source position.
     *
     * @param position The source position to run to.
     * @param context The suspend context in which the run operation is performed.
     */
    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        logger.debug("User request: runToPosition")
        command.add(ApiQuery.VOID)
    }

    /**
     * Updates the stack frames of the debugging process.
     *
     * @return the StepInfo object representing the current step information, or null if the stack update failed
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

    /**
     * Merges breakpoints based on the given parameters.
     *
     * @param first Indicates whether it is the first merge operation.
     * @param frame The XStack.XFrame object representing the current stack frame.
     * @return A list of integers representing the merged breakpoints.
     */
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

    /**
     * Adds a breakpoint to the specified file at the given line number.
     *
     * @param file The PlFunctionSource representing the file where the breakpoint is to be added.
     * @param breakpoint The PlLineBreakpointProperties representing the breakpoint to be added.
     */
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

    /**
     * Drops a breakpoint for a given file and breakpoint.
     *
     * @param file The PlFunctionSource object representing the file containing the breakpoint.
     * @param breakpoint The XLineBreakpoint<PlLineBreakpointProperties> object representing the breakpoint to be dropped.
     */
    fun dropBreakpoint(file: PlFunctionSource, breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
        logger.debug("dropBreakpoint: file=${file.name}, line=${breakpoint.line}")
        executor.updateBreakPoint(
            ApiQuery.DROP_BREAKPOINT,
            file.oid,
            breakpoint.line - file.start
        )
    }

    /**
     * Returns the XDebuggerEditorsProvider implementation to be used by this PlProcess class.
     *
     * @return The XDebuggerEditorsProvider implementation.
     */
    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return PlEditorProvider.INSTANCE
    }

    /**
     * Checks if the debugging process can initialize breakpoints.
     *
     * @return true if the debugging process can initialize breakpoints, false otherwise.
     */
    override fun checkCanInitBreakpoints(): Boolean {
        return true
    }

    /**
     * Checks if the debugger is currently able to perform commands.
     *
     * @return true if the debugger is able to perform commands, false otherwise.
     */
    override fun checkCanPerformCommands(): Boolean {
        return proxyTask.running.get()
    }

    /**
     * Returns an array of breakpoint handlers for the debugging process.
     *
     * @return An array of XBreakpointHandler objects that handle breakpoints.
     */
    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> {
        return arrayOf(breakPointHandler)
    }


    /**
     * The XContext class is an inner class that extends the XSuspendContext class.
     * It provides an implementation for the XSuspendContext's abstract methods.
     */
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

    /**
     * The `BreakPointHandler` class is responsible for handling breakpoints in the debugging process.
     * It implements the `XBreakpointHandler` and `XBreakpointListener` interfaces to handle breakpoint-related events.
     *
     * @constructor Creates a new instance of the `BreakPointHandler` class.
     */
    inner class BreakPointHandler :
        XBreakpointHandler<XLineBreakpoint<PlLineBreakpointProperties>>(PlLineBreakpointType::class.java),
        XBreakpointListener<XLineBreakpoint<PlLineBreakpointProperties>> {

        override fun registerBreakpoint(breakpoint: XLineBreakpoint<PlLineBreakpointProperties>) {
            executor.setInfo("registerBreakpoint: ${breakpoint.fileUrl} => ${breakpoint.line}")
            runReadAction {
                val path = breakpoint.fileUrl.removePrefix(PlVirtualFileSystem.PROTOCOL_PREFIX)
                val file = PlVirtualFileSystem.Util.getInstance().findFileByPath(path)
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
                val file = PlVirtualFileSystem.Util.getInstance().findFileByPath(path)
                if (file != null && readyToAcceptBreakPoint()) {
                    dropBreakpoint(file, breakpoint)
                }
                breakpoints[path]?.removeIf {
                    it.line == breakpoint.line
                }
            }
        }
    }

    /**
     * Checks if the debugging process is ready to accept a breakpoint.
     *
     * @return True if the proxy task is running and the executor is not waiting for completion, false otherwise.
     */
    fun readyToAcceptBreakPoint(): Boolean = proxyTask.running.get() && !executor.waitingForCompletion

    /**
     * This inner class represents a proxy task that is executed in the background.
     * It extends the Task.Backgroundable class.
     *
     * @param project The Project object associated with the task.
     * @param title The title of the task.
     */
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

    /**
     * Represents a proxy indicator for a background process.
     * This class extends the BackgroundableProcessIndicator class.
     * It provides a method to display information about the progress of the process.
     *
     * @param task The ProxyTask associated with the indicator.
     */
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
     * A nested inner class that extends the Thread class. This class represents a thread that performs operations
     * related to debugging.
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

    /**
     * Represents information about a step in a process.
     *
     * @property pos The position of the step.
     * @property total The total number of steps.
     * @property ratio The ratio of the step.
     */
    data class StepInfo(val pos: Int, val total: Int, val ratio: Double)

}
