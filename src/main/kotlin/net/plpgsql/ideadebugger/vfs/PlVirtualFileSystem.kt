/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.vfs

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.dummy.DummyCachingFileSystem
import java.util.*


class PlVirtualFileSystem : DummyCachingFileSystem<PlFunctionSource>(PROTOCOL) {

    override fun findFileByPathInner(path: String): PlFunctionSource?{
        return null
    }

    init {

        //createRoot("root")
    }

    override fun doRenameFile(vFile: VirtualFile, newName: String) {}

    companion object {
        const val PROTOCOL: String = "plpgsql"
        const val PROTOCOL_PREFIX: String = "$PROTOCOL://"
        private val INSTANCE = Objects.requireNonNull(VirtualFileManager.getInstance().getFileSystem(PROTOCOL)) as PlVirtualFileSystem
        fun getInstance(): PlVirtualFileSystem = INSTANCE
    }

    fun registerNewDefinition(file: PlFunctionSource): PlFunctionSource {
        runInEdt {
            runWriteAction {
                fileRenamed(file, null, "", file.name)
            }
        }
        return file
    }

}