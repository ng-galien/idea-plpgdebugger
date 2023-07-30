/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.vfs

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.command.PlApiFunctionDef
import net.plpgsql.ideadebugger.command.PlApiStackFrame
import net.plpgsql.ideadebugger.command.PlExecutor

val vfs = PlVirtualFileSystem.Util.getInstance()

fun refreshFileFromStackFrame(project: Project, executor: PlExecutor, stack: PlApiStackFrame): PlFunctionSource =
    ensureFileFromDB(executor, stack.oid).let { def ->
        fileFromVFS(def.oid)?.updateContentFromDB(def)
            ?: vfs.registerNewDefinition(PlFunctionSource(project, def, def.md5))
    }.let { file ->
        if (stack.level == 0) {
            runInEdt {
                FileEditorManager.getInstance(project).openFile(file, true, true)
            }
        }
        file
    }

fun searchFileFromOid(project: Project, executor: PlExecutor, oid: Long): PlFunctionSource? =
    vfs.findFileByPath("$oid") ?:
    searchFileFromDB(executor, oid)?.let { def ->
        vfs.registerNewDefinition(PlFunctionSource(project, def, def.md5))
    }

fun fileFromVFS(oid: Long): PlFunctionSource? = vfs.findFileByPath("$oid")

fun ensureFileFromDB(executor: PlExecutor, oid: Long): PlApiFunctionDef {
    return executor.getFunctionDef(oid) ?: throw IllegalStateException("Can't find file for $oid")
}

fun searchFileFromDB(executor: PlExecutor, oid: Long): PlApiFunctionDef? {
    return executor.getFunctionDef(oid)
}