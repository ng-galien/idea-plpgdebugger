/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.vfs

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.dummy.DummyCachingFileSystem
import net.plpgsql.ideadebugger.node.SourceFile
import net.plpgsql.ideadebugger.service.SourceLoaded
import net.plpgsql.ideadebugger.service.publishEvent
import java.util.*



class PlVirtualFileSystem : DummyCachingFileSystem<PlFunctionSource>(PROTOCOL) {

    override fun findFileByPathInner(path: String): PlFunctionSource?{
        return null
    }

    override fun doRenameFile(vFile: VirtualFile, newName: String) {}

    object Util {
        private val INSTANCE = Objects.requireNonNull(VirtualFileManager.getInstance().getFileSystem(PROTOCOL))
                as PlVirtualFileSystem

        fun getInstance(): PlVirtualFileSystem = INSTANCE
    }

    companion object {
        const val PROTOCOL: String = "plpgsql"
        const val PROTOCOL_PREFIX: String = "$PROTOCOL://"
    }

    fun registerNewDefinition(file: PlFunctionSource): PlFunctionSource {
        runInEdt {
            runWriteAction {
                fileRenamed(file, null, "", file.name)
            }
        }
        publishEvent(SourceLoaded(file.project, SourceFile(file.name, file.path)))
        return file
    }

}