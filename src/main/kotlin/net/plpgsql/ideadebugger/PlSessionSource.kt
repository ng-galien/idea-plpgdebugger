/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.sql.SqlFileType
import com.intellij.util.FileContentUtilCore
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.properties.Delegates


/**
 * TODO compute local changes
 *
A PlFile represents a database procedure
@property def primary procedure definition
@properties the stack which owns this definition
 */
class PlSessionSource(
    def: PlStackFrame
) : PlFile() {

    private var stack: XStack? = null
    private val breakPoints: MutableMap<Int, Boolean> = mutableMapOf()
    private var first: Boolean = true
    var firstPos: Int = 0
        private set
    var stackPos: Int = 0
    private var offset: Int by Delegates.notNull()
    private var source: String = def.source
    private var oid: Long = def.oid
    private var func_schema: String = def.schema
    private var func_name: String = def.name

    init {
        computeOffset()
    }

    fun attachStack(xStack: XStack): PlSessionSource {
        stack = xStack
        if (first) {
            firstPos = stackPos
            //Remove all breakpoints
            stack!!.executor.getBreakPoints().forEach {
                updateBreakPoint(SQLQuery.DROP_BREAKPOINT, it.line)
            }
            // Set breakpoints
            breakPoints.filter {
                !it.value
            }.forEach {
                updateBreakPoint(SQLQuery.ADD_BREAKPOINT, it.key)
                breakPoints[it.key] = true
            }
            first = false
        }
        return this
    }

    fun updateFrame(frame: PlStackFrame): PlSessionSource {
        source = frame.source
        stackPos = frame.line
        computeOffset()
        return this
    }

    private fun computeOffset() {
        var res = 0;
        source.split("\n").forEachIndexed { l, line ->
            if (line.lowercase().startsWith("as \$function\$")) res = l - 1
        }
        offset = res;
    }

    /**
     * Returns true if this procedure has further step
     */
    fun canContinue(): Boolean = false//stackPos == firstPos && !breakPoints.contains(firstPos) && breakPoints.isNotEmpty()

    /**
     *
     */
    fun isOnBreakpoint(): Boolean = breakPoints[stackPos] ?: false

    fun getPosition(): XSourcePosition? {
        return XDebuggerUtil.getInstance().createPosition(this, stackPos + offset)
    }

    /**
     * Reset all contextual values, called when debugging session ends
     */
    fun unload() {
        first = true
        stackPos = 0
        stack = null
        breakPoints.clear()
    }

    fun isUnloaded(): Boolean {
        return first && stackPos == 0
    }

    fun addSourceBreakpoint(sourcePos: Int): Boolean {
        val remotePos = breakpointPosFromSource(sourcePos)
        updateBreakPoint(SQLQuery.ADD_BREAKPOINT, remotePos)
        breakPoints[remotePos] = stack != null
        return true
    }

    fun removeSourceBreakpoint(sourcePos: Int) {
        val remotePos = breakpointPosFromSource(sourcePos)
        updateBreakPoint(SQLQuery.DROP_BREAKPOINT, remotePos)
        breakPoints.remove(remotePos)
    }

    private fun updateBreakPoint(query: SQLQuery, line: Int): Boolean {
        if (stack != null) {
            return stack!!.executor.updateBreakPoint(query, oid, line)
        }
        return true
    }

    private fun breakpointPosFromSource(sourcePos: Int): Int = sourcePos - offset

    override fun isDirectory(): Boolean = false

    override fun getParent(): VirtualFile? = EMPTY_PARENT

    override fun getChildren(): Array<VirtualFile> = EMPTY_CHILDREN

    override fun getCharset(): Charset = Charsets.UTF_8

    override fun getInputStream(): InputStream {
        return ByteArrayInputStream(source.toByteArray(charset));
    }

    override fun getOutputStream(
        requestor: Any?,
        newModificationStamp: Long,
        newTimeStamp: Long): OutputStream = OutStream()

    override fun contentsToByteArray(): ByteArray = source.toByteArray(charset)

    override fun getLength(): Long = source.length as Long

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

    override fun getPath(): String = "${oid}"

    override fun getUrl(): String {
        return "${PlVFS.PROTOCOL_PREFIX}$name"
    }

    override fun getPresentableName(): String = name

    override fun getName(): String = "${func_schema}.${func_name}[${oid}].sql"

    override fun isWritable(): Boolean = true

    override fun getFileType(): FileType {
        return SqlFileType.INSTANCE
    }

    inner class OutStream: ByteArrayOutputStream() {
        override fun close() {
            source = String(buf, 0, count, charset)
            FileContentUtilCore.reparseFiles(this@PlSessionSource)
        }
    }


    companion object {
        val EMPTY_PARENT: VirtualFile? = null
        val EMPTY_CHILDREN = arrayOf<VirtualFile>()
    }
}