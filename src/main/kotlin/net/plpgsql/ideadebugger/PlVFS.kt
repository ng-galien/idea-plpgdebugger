/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.*


const val PL_PROTOCOL = "plpgsql"
const val PL_PROTOCOL_PREFIX = "plpgsql://"



class PlVFS : DeprecatedVirtualFileSystem() {

    private val fileRegistry = mutableMapOf<String, PlFile>()

    fun register(file: PlFile): PlFile {
        fileRegistry[file.path] = file
        return file
    }

    fun all(): List<PlFile> = fileRegistry.values.toList()

    override fun getProtocol(): String = PL_PROTOCOL

    override fun findFileByPath(path: String): VirtualFile? = fileRegistry[path]

    override fun refresh(asynchronous: Boolean) {}

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

    override fun isReadOnly(): Boolean = false

    companion object {
        fun getInstance(): PlVFS =
            Objects.requireNonNull(VirtualFileManager.getInstance().getFileSystem(PL_PROTOCOL)) as PlVFS
    }

}