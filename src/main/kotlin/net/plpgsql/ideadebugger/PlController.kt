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

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.SearchPath
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import net.plpgsql.ideadebugger.command.PlExecutor
import net.plpgsql.ideadebugger.run.PlProcess
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * This class represents the PL Controller which extends the SqlDebugController.
 * It is responsible for managing the PL debugging process.
 *
 * @property project The project associated with the PL controller.
 * @property connectionPoint The database connection point.
 * @property searchPath The search path for the PL controller.
 * @property callDefinition The call definition for the PL controller.
 */
class PlController(
    val project: Project,
    val connectionPoint: DatabaseConnectionPoint,
    val searchPath: SearchPath?,
    val callDefinition: CallDefinition,
) : SqlDebugController() {

    private lateinit var plProcess: PlProcess
    private lateinit var xSession: XDebugSession
    private val settings = PlDebuggerSettingsState.getInstance().state
    private var executor: PlExecutor? = null
    private val logger = logger<PlController>()
    private val listenerReady = CountDownLatch(1)
    private val initializationResolved = AtomicBoolean(false)
    private val initializationSucceeded = AtomicBoolean(false)
    private val initializationFailure = AtomicReference<Throwable?>(null)

    override fun getReady() {
        logger.info("PL/pg debugger controller is ready")
        val windowLister = ToolListener()
        project.messageBus.connect(xSession.consoleView).subscribe(ToolWindowManagerListener.TOPIC, windowLister)
    }

    override fun initLocal(session: XDebugSession): XDebugProcess {
        xSession = session
        plProcess = PlProcess(
            session = session,
            onListenerReady = ::signalListenerReady,
            onInitializationFailed = ::signalInitializationFailed
        )
        logger.info(
            "Queueing PL/pg debugger initialization " +
                "(mode=${callDefinition.debugMode}, selectionOk=${callDefinition.selectionOk}, " +
                "canDebug=${callDefinition.canDebug()}, queryLength=${callDefinition.query.length})"
        )
        object : Task.Backgroundable(project, "Getting Auxiliary Connection", true) {
            override fun run(indicator: ProgressIndicator) {
                initializeLocal(plProcess)
            }
        }.queue()
        return plProcess
    }

    private fun initializeLocal(process: PlProcess) {
        var stage = "acquiring auxiliary connection"
        var localExecutor: PlExecutor? = null
        try {
            logger.info("PL/pg debugger initialization started (thread=${Thread.currentThread().name})")
            val maybeConnection = getAuxiliaryConnection(project, connectionPoint, searchPath)

            if (maybeConnection == null) {
                signalInitializationFailed(
                    IllegalStateException("Unable to acquire the auxiliary database connection")
                )
                logger.warn("PL/pg debugger initialization stopped: auxiliary connection is unavailable")
                notifyInitializationFailure("Unable to acquire the auxiliary database connection.")
                stopSession()
                return
            }

            stage = "attaching the debugger process"
            localExecutor = PlExecutor(maybeConnection)
            if (process.isStopped() || project.isDisposed) {
                signalInitializationFailed(
                    ProcessCanceledException(RuntimeException("Debugger process or project was stopped"))
                )
                logger.info("PL/pg debugger initialization canceled before process attachment")
                localExecutor.cancelAndCloseConnection()
                return
            }

            executor = localExecutor
            process.initialize(localExecutor)
            Disposer.register(xSession.consoleView, localExecutor)
            logger.info("PL/pg debugger executor attached")

            @Suppress("DialogTitleCapitalization")
            if (!callDefinition.canDebug()) {
                signalInitializationFailed(IllegalArgumentException("The selected SQL statement cannot be debugged"))
                logger.warn(
                    "PL/pg debugger initialization stopped: invalid selection " +
                        "(selectionOk=${callDefinition.selectionOk}, mode=${callDefinition.debugMode})"
                )
                notifyInvalidSelection()
                localExecutor.cancelAndCloseConnection()
                stopSession()
                return
            }

            stage = "checking the PostgreSQL debugger extension"
            val diag = localExecutor.checkDebugger()
            logger.info(
                "PL/pg debugger diagnostic completed " +
                    "(customCommand=${diag.customCommandOk}, sharedLibrary=${diag.sharedLibraryOk}, " +
                    "extension=${diag.extensionOk}, activity=${diag.activityOk})"
            )
            if (settings.failExtension || !extensionOk(diag)) {
                signalInitializationFailed(IllegalStateException("The PostgreSQL debugger extension diagnostic failed"))
                logger.warn("PL/pg debugger extension diagnostic failed")
                showExtensionDiagnostic(project, diag)
                localExecutor.cancelAndCloseConnection()
                stopSession()
                return
            }

            if (settings.failDetection) {
                localExecutor.setError("[FAKE]Function not found: schema=${callDefinition.schema}, name=${callDefinition.routine}")
            }

            if (localExecutor.interrupted()) {
                signalInitializationFailed(IllegalStateException("Debugger executor interrupted after diagnostic"))
                logger.warn("PL/pg debugger initialization interrupted after extension diagnostic")
                stopSession()
                return
            }
            if (process.isStopped()) {
                logger.info("PL/pg debugger initialization canceled after extension diagnostic")
                localExecutor.cancelAndCloseConnection()
                return
            }

            stage = "identifying the selected routine"
            logger.info("Identifying PL/pg routine through DatabaseTools")
            callDefinition.identify()
            logger.info(
                "DatabaseTools routine identification result " +
                    "(schema=${callDefinition.schema}, routine=${callDefinition.routine}, oid=${callDefinition.oid})"
            )

            if (!callDefinition.canStartDebug()) {
                logger.info("Falling back to PostgreSQL catalog routine identification")
                callDefinition.identify(localExecutor)
                logger.info(
                    "Catalog routine identification result " +
                        "(schema=${callDefinition.schema}, routine=${callDefinition.routine}, oid=${callDefinition.oid})"
                )
            }

            if (!callDefinition.canStartDebug()) {
                localExecutor.setError(
                    "Function not found: schema=${callDefinition.schema}, name=${callDefinition.routine}"
                )
            }

            if (localExecutor.interrupted()) {
                signalInitializationFailed(IllegalStateException("Unable to identify the selected PostgreSQL routine"))
                logger.warn("PL/pg debugger initialization interrupted during routine identification")
                stopSession()
                return
            }
            if (process.isStopped()) {
                logger.info("PL/pg debugger initialization canceled before start")
                localExecutor.cancelAndCloseConnection()
                return
            }

            stage = "starting the debugger process"
            if (!process.startDebug(callDefinition)) {
                signalInitializationFailed(
                    ProcessCanceledException(RuntimeException("Debugger process was stopped before startup"))
                )
                localExecutor.cancelAndCloseConnection()
                return
            }
            logger.info(
                "PL/pg debugger process started " +
                    "(mode=${callDefinition.debugMode}, oid=${callDefinition.oid}, routine=${callDefinition.routine})"
            )
        } catch (e: ProcessCanceledException) {
            signalInitializationFailed(e)
            logger.info("PL/pg debugger initialization canceled during $stage")
            localExecutor?.let { runCatching { it.cancelAndCloseConnection() } }
            throw e
        } catch (e: Throwable) {
            signalInitializationFailed(e)
            logger.error("PL/pg debugger initialization failed during $stage", e)
            localExecutor?.let { runCatching { it.cancelAndCloseConnection() } }
            notifyInitializationFailure(
                "Initialization failed while $stage: ${e.message ?: e.javaClass.simpleName}"
            )
            stopSession()
        }
    }

    private fun signalListenerReady() {
        if (initializationResolved.compareAndSet(false, true)) {
            initializationSucceeded.set(true)
            listenerReady.countDown()
            logger.info("PL/pg debugger readiness barrier opened")
        }
    }

    private fun signalInitializationFailed(error: Throwable) {
        if (initializationResolved.compareAndSet(false, true)) {
            initializationFailure.set(error)
            listenerReady.countDown()
            logger.warn("PL/pg debugger readiness barrier failed", error)
        }
    }

    private fun notifyInvalidSelection() {
        Notification(
            "PL/pg Notifications",
            "PL/pg Debugger",
            "You must select only one valid query",
            NotificationType.WARNING
        ).notify(project)
    }

    private fun notifyInitializationFailure(message: String) {
        Notification(
            "PL/pg Notifications",
            "PL/pg Debugger initialization failed",
            "$message Full details were written to idea.log.",
            NotificationType.ERROR
        ).notify(project)
    }

    private fun stopSession() {
        logger.info("Stopping PL/pg debugger session")
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                xSession.stop()
            }
        }
    }

    override fun initRemote(connection: DatabaseConnection) {
        logger.info("Remote debug request is waiting for the PL/pg listener")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!listenerReady.await(100, TimeUnit.MILLISECONDS)) {
            ProgressManager.checkCanceled()
            if (project.isDisposed) {
                throw ProcessCanceledException()
            }
            if (System.nanoTime() >= deadline) {
                val error = IllegalStateException("Timed out while waiting for the PL/pg debugger listener")
                signalInitializationFailed(error)
                notifyInitializationFailure(error.message!!)
                stopSession()
                throw error
            }
        }

        initializationFailure.get()?.let {
            throw IllegalStateException("PL/pg debugger initialization failed before query execution", it)
        }
        check(initializationSucceeded.get()) {
            "PL/pg debugger readiness barrier completed without a listener"
        }
        logger.info("PL/pg listener ready; allowing the SQL debug request to execute")
    }

    override fun debugBegin() {
        logger.info("PL/pg debugger debugBegin")
    }

    override fun debugEnd() {
        logger.info("PL/pg debugger debugEnd")
        if (callDefinition.debugMode == DebugMode.DIRECT) {
            stopSession()
        }
    }

    override fun close() {
        logger.info("Closing PL/pg debugger controller")
        if (callDefinition.debugMode == DebugMode.DIRECT) {
            closeDebugWindow(xSession.sessionName)
            Disposer.dispose(DatabaseSessionManager.getSession(project, connectionPoint))
        }
    }

    private fun closeDebugWindow(sessionName: String) {
        runInEdt {
            ToolWindowManager.getInstance(project).getToolWindow("Debug")?.let { toolWindow ->
                toolWindow.contentManager.contents.firstOrNull {
                    it.tabName == sessionName
                }?.let {
                    it.manager?.removeContent(it, true)
                }
            }
        }
    }

    inner class ToolListener : ToolWindowManagerListener {

        private var debugWindow: ToolWindow? = null
        private var first: Boolean = false
        override fun toolWindowShown(toolWindow: ToolWindow) {

            if (toolWindow.id == "Debug") {
                debugWindow = toolWindow
                first = true
            }
            if (first && toolWindow.id != "Debug") {
                debugWindow?.show()
                first = false
            }
        }
    }
}
