/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.vfs

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.command.PlApiFunctionDef
import net.plpgsql.ideadebugger.command.PlApiStackFrame
import net.plpgsql.ideadebugger.command.DBExecutor

val vfs = PlVirtualFileSystem.Util.getInstance()

fun refreshFileFromStackFrame(project: Project, executor: DBExecutor, stack: PlApiStackFrame): PlFunctionSource =
    ensureFileFromDB(executor, stack.oid).let { def ->
        fileFromVFS(def.oid)?.updateContentFromDB(def)
            ?: vfs.registerNewDefinition(PlFunctionSource(project, def, def.md5))
    }

fun showFile(project: Project, file: PlFunctionSource) =
    runInEdt {
        FileEditorManager.getInstance(project).openFile(file, true, true)
    }

fun searchFileFromOid(project: Project, executor: DBExecutor, oid: Long): PlFunctionSource? =
    vfs.findFileByPath("$oid") ?:
    searchFileFromDB(executor, oid)?.let { def ->
        vfs.registerNewDefinition(PlFunctionSource(project, def, def.md5))
    }

fun fileFromVFS(oid: Long): PlFunctionSource? = vfs.findFileByPath("$oid")

fun ensureFileFromDB(executor: DBExecutor, oid: Long): PlApiFunctionDef {
    return searchFileFromDB(executor, oid) ?: throw IllegalStateException("File with oid $oid not found")
}

fun searchFileFromDB(executor: DBExecutor, oid: Long): PlApiFunctionDef? {
    return executor.getFunctionDef(oid)
}