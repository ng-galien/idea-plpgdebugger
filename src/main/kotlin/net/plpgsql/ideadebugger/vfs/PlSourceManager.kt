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
import net.plpgsql.ideadebugger.command.PlApiFunctionDef
import net.plpgsql.ideadebugger.command.PlApiStackFrame
import net.plpgsql.ideadebugger.command.PlExecutor

class PlSourceManager(private val project: Project, private val executor: PlExecutor) {

    private val vfs = PlVirtualFileSystem.Util.getInstance()

    fun update(stack: PlApiStackFrame): PlFunctionSource? {
        grabFromDB(stack.oid)?.let { def ->
            grabFromVFS(def.oid)?.let { vfsFile ->
                if (!compareMD5(def, vfsFile)) {
                    updateFileContent(vfsFile, def)
                }
            }?: run {
                vfs.registerNewDefinition(PlFunctionSource(project, def, def.md5))
            }.let {
                runInEdt {
                    FileEditorManager.getInstance(project).openFile(it, true, true)
                }
                return it
            }
        }
        return vfs.findFileByPath("${stack.oid}")
    }

    private fun updateFileContent(vfsFile: PlFunctionSource, def: PlApiFunctionDef) {
        runReadAction {
            PsiManager.getInstance(project).findFile(vfsFile)
        }?.let { psi ->
            PsiDocumentManager.getInstance(project).getDocument(psi)?.let { doc ->
                runInEdt {
                    runWriteAction {
                        doc.setText(def.source)
                    }
                }
            }
        }
    }

    private fun grabFromDB(oid: Long): PlApiFunctionDef? {
        return executor.getFunctionDef(oid)
    }

    private fun grabFromVFS(oid: Long): PlFunctionSource? = runReadAction {
        vfs.findFileByPath("$oid")
    }

    private fun compareMD5(inDB: PlApiFunctionDef, inVFS: PlFunctionSource): Boolean {
        return inDB.md5 == inVFS.md5
    }
}
