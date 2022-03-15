/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import net.plpgsql.ideadebugger.vfs.PlFunctionSource
import net.plpgsql.ideadebugger.vfs.PlVirtualFileSystem

class PlSourceManager(val project: Project, val executor: PlExecutor) {

    fun update(stack: PlApiStackFrame): PlFunctionSource? {
        var existing = PlVirtualFileSystem.getInstance().findFileByPath("${stack.oid}")
        val reload = (existing == null) || (existing.md5 != stack.md5)
        if (reload) {
            closeFile(existing)
            val def = executor.getFunctionDef(stack.oid)
            existing = PlVirtualFileSystem.getInstance().registerNewDefinition(
                PlFunctionSource(project, def)
            )
        } else {
            checkFile(existing)
        }
        return existing
    }

    private fun closeFile(file: PlFunctionSource?) {
        if (file == null) {
            return
        }
        runInEdt {
            val editorManager = FileEditorManagerEx.getInstanceEx(project)
            editorManager.closeFile(file)
        }
    }

    private fun checkFile(file: PlFunctionSource?) {
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
}