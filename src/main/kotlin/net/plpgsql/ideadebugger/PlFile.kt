/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.nio.charset.Charset

abstract class PlFile: VirtualFile() {
    override fun getCharset(): Charset = Charsets.UTF_8

    override fun getFileSystem(): VirtualFileSystem = PlVirtualFileSystem.getInstance()

    override fun isValid(): Boolean = PlVirtualFileSystem.getInstance().findFileByPath(path) != null

    override fun getTimeStamp(): Long = -1L
}