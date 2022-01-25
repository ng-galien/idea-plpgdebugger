/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.connection.throwable.info.WarningInfo
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.datagrid.DataAuditors
import com.intellij.database.datagrid.DataConsumer
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.debugger.SqlDebugController
import com.intellij.database.util.SearchPath
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiDocumentListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlBlockStatement
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.ui.content.Content
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import java.util.regex.Pattern

class PlController(
    val facade: PlFacade,
    val project: Project,
    val connectionPoint: DatabaseConnectionPoint,
    val ownerEx: DataRequest.OwnerEx,
    val virtualFile: VirtualFile?,
    val rangeMarker: RangeMarker?,
    val searchPath: SearchPath?,
    val callExpression: SqlFunctionCallExpression?,
) : SqlDebugController() {

    private val auditor = QueryAuditor()
    private val consumer = QueryConsumer()
    private val psiListener = PsiListener()
    private val pattern = Pattern.compile(".*PLDBGBREAK:([0-9]+).*")
    private lateinit var plProcess: PlProcess
    lateinit var xSession: XDebugSession
    internal val windowLister = ToolListener()

    val executor = PlExecutor(this)
    private var busConnection = project.messageBus.connect()

    init {
        println("controller: init")
        busConnection.subscribe(ToolWindowManagerListener.TOPIC, windowLister)
    }

    fun closeFile(file: PlFunctionSource?) {
        if (file == null) {
            return
        }
        runInEdt {
            val editorManager = FileEditorManagerEx.getInstanceEx(project)
            editorManager.closeFile(file)
        }
    }

    fun checkFile(file: PlFunctionSource?) {
        file?.let { source ->
            runReadAction {
                PsiManager.getInstance(project).findFile(source)?.let { psi ->
                    PsiDocumentManager.getInstance(project).getDocument(psi)?.let { doc ->
                        if (doc.text != source.content) {
                            runInEdt {
                                runWriteAction {
                                    doc.setText(source.content)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getReady() {
        executor.setDebug("Controller: getReady")
        executor.checkExtension()
        if (executor.interrupted()) {
            return
        }
        if (callExpression != null) {
            val callDef = parseFunctionCall(callExpression)
            assert(callDef.first.isNotEmpty() && callDef.first.size <= 2) { "Error while parsing ${callExpression.text}" }
            executor.searchCallee(callDef.first, callDef.second)
            if (executor.interrupted()) {
                return
            }
        } else {
            executor.setError("Invalid call expression")
            executor.interrupted()
        }
    }


    override fun initLocal(session: XDebugSession): XDebugProcess {
        xSession = session
        plProcess = PlProcess(this)
        return plProcess
    }

    override fun initRemote(connection: DatabaseConnection) {
        executor.setDebug("Controller: initRemote")
        executor.startDebug(connection)
        if (!executor.interrupted()) {
            ownerEx.messageBus.addAuditor(auditor)
            ownerEx.messageBus.addConsumer(consumer)
        }
    }

    override fun debugBegin() {
        executor.setDebug("Controller: debugBegin")
    }


    override fun debugEnd() {
        println("controller: debugEnd")
        busConnection.disconnect()
    }

    override fun close() {
        println("controller: close")
        //windowLister.close()

    }

    inner class QueryAuditor : DataAuditors.Adapter() {

        override fun warn(context: DataRequest.Context, info: WarningInfo) {
            executor.setWarning("[CALLER]: ${info.message}")
            if (!executor.hasError() && context.request.owner == ownerEx) {
                val matcher = pattern.matcher(info.message)
                if (matcher.matches()) {
                    val port = matcher.group(1).toInt()
                    executor.attachToPort(port)
                    if (!executor.interrupted()) {
                        plProcess.startDebug()
                    }
                }
            }
        }

        override fun fetchStarted(context: DataRequest.Context, index: Int) {
            if (context.request.owner == ownerEx) {
                executor.terminate()
            }
        }
    }

    inner class QueryConsumer : DataConsumer.Adapter() {
        override fun afterLastRowAdded(context: DataRequest.Context, total: Int) {
            if (context.request.owner == ownerEx) {
                //ownerExConnection?.remoteConnection?.close()
            }
        }

    }

    inner class PsiListener: PsiDocumentListener {
        override fun documentCreated(document: Document, psiFile: PsiFile?, project: Project) {
            println("documentCreated ${psiFile?.name}")
        }

        override fun fileCreated(file: PsiFile, document: Document) {
            println("fileCreated ${file.name}")
            document.addDocumentListener(DocListener())
        }
    }

    inner class DocListener: DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            println("documentChanged $event")
        }

        override fun bulkUpdateStarting(document: Document) {
            println("documentChanged $document")
        }

        override fun bulkUpdateFinished(document: Document) {
            println("bulkUpdateFinished $document")
        }
    }

    inner class ToolListener : ToolWindowManagerListener {

        private var window: ToolWindow? = null
        private var first: Boolean = false
        private var acutal: Content? = null
        var hasShown = false

        override fun toolWindowShown(toolWindow: ToolWindow) {
            if (toolWindow.id == "Debug") {
                window = toolWindow
                first = true
            }
            if (first && toolWindow.id != "Debug") {
                window?.show()
                acutal = window?.contentManager?.contents?.find {
                    it.tabName == xSession.sessionName
                }
                first = false
                hasShown = true
                executor.updateInfo()
            }
        }

        fun close() {
            runInEdt {
                windowLister.acutal?.let { window?.contentManager?.removeContent(it, true) }
            }
        }
    }
}


