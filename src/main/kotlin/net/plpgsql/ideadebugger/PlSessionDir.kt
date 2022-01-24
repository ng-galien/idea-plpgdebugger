/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class PlSessionDir(private var session: Int):  PlFile() {

    override fun getName(): String = "$session"

    override fun getPath(): String = "${PlVFS.PROTOCOL_PREFIX}$name"

    override fun isWritable(): Boolean = true

    override fun isDirectory(): Boolean = true

    override fun getParent(): VirtualFile? = null

    override fun getChildren(): Array<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw IOException("Invalid operation")
    }

    override fun contentsToByteArray(): ByteArray {
        throw IOException("Invalid operation")
    }

    override fun getTimeStamp(): Long = -1L

    override fun getLength(): Long = 0L

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        TODO("Not yet implemented")
    }

    override fun getInputStream(): InputStream {
        throw IOException("Invalid operation")
    }

}