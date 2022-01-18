/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile

const val PL_PROTOCOL = "pl://"

object PlVFS : DeprecatedVirtualFileSystem() {

    private val fileRegistry = mutableMapOf<String, VirtualFile>()

    fun register(file: PlFile) {
        fileRegistry[file.path] = file
    }

    override fun getProtocol(): String = PL_PROTOCOL

    override fun findFileByPath(path: String): VirtualFile? = fileRegistry[path]

    override fun refresh(asynchronous: Boolean) {}

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

    override fun isReadOnly(): Boolean = false

}