/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.dummy.DummyCachingFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.*


class PlVFS : DummyCachingFileSystem<PlFile>(PROTOCOL) {

    companion object {
        const val PROTOCOL: String = "plpgsql"
        const val PROTOCOL_PREFIX: String = "$PROTOCOL://"
        private val INSTANCE = Objects.requireNonNull(VirtualFileManager.getInstance().getFileSystem(PROTOCOL)) as PlVFS
        fun getInstance(): PlVFS = INSTANCE
    }

    override fun findFileByPathInner(path: String): PlFile? = findFileByPath(path)

    fun refresh(frame: PlStackFrame) {
        val file = findFileByPath("${frame.oid}")
        if (file is PlSessionSource) {
            file.updateFrame(frame)
        }
    }

}