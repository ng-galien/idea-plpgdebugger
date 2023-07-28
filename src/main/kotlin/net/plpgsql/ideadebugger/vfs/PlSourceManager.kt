/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.vfs

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import net.plpgsql.ideadebugger.command.PlApiStackFrame
import net.plpgsql.ideadebugger.command.PlExecutor

class PlSourceManager(private val project: Project, private val executor: PlExecutor) {

    private val vfs = PlVirtualFileSystem.getInstance()

    fun update(stack: PlApiStackFrame): PlFunctionSource? {

        var virtualFile = runReadAction {
            vfs.findFileByPath("${stack.oid}")
        }

        if (virtualFile == null) {
            val def = executor.getFunctionDef(stack.oid)
            if (def != null) {
                virtualFile = vfs.registerNewDefinition(PlFunctionSource(project, def))
            }
        }

        if (virtualFile != null) {
            runReadAction {
                PsiManager.getInstance(project).findFile(virtualFile)
            }?.let { psi ->
                PsiDocumentManager.getInstance(project).getDocument(psi)?.let { doc ->
                    if (doc.text != virtualFile.content) {
                        runInEdt {
                            runWriteAction {
                                doc.setText(virtualFile.content)
                            }
                        }
                    }
                }
            }
        }

        if (virtualFile != null) {
            runInEdt {
                FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
            }
        }

        return virtualFile
    }

    fun getVirtualFile(oid: Long): PlFunctionSource? {
        return runReadAction {
            vfs.findFileByPath("${oid}")
        }
    }

    fun getSourceFromDatabase(oid: Long): PlFunctionSource? = executor.getFunctionDef(oid)?.let {
        vfs.registerNewDefinition(PlFunctionSource(project, it))
    }


}