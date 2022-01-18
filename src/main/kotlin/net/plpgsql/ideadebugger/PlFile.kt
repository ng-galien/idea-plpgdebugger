/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.sql.SqlFileType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import kotlin.properties.Delegates
import kotlin.test.assertTrue

class PlFile(
    private val def: PlFunctionDef,
    private var stack: XStack?
) : LightVirtualFile(
    "${def.schema}.${def.name}(${def.oid}).sql",
    SqlFileType.INSTANCE,
    def.source,
) {

    private val breakPoints: MutableMap<Int, Boolean> = mutableMapOf()
    private var first: Boolean = true
    var firstPos: Int = 0
        private set
    var stackPos: Int = 0
        private set
    private var offset: Int by Delegates.notNull()

    init {
        var res = 0;
        def.source.split("\n").forEachIndexed { l, line ->
            if (line.lowercase().startsWith("as \$function\$")) res = l - 1
        }
        offset = res;
    }

    fun canContinue(): Boolean = stackPos == firstPos && !breakPoints.contains(firstPos) && breakPoints.isNotEmpty()

    fun isOnBreakpoint(): Boolean = breakPoints[stackPos] ?: false

    fun updateStack(s: XStack, pos: Int): PlFile {
        stack = s
        stackPos = pos
        if (first) {
            firstPos = stackPos
            //Remove all breakpoints
            plGetPlStackBreakPoint(stack!!.proc).forEach {
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

    fun getPosition(): XSourcePosition? {
        return XDebuggerUtil.getInstance().createPosition(this, stackPos + offset)
    }

    fun unload() {
        first = true
        stackPos = 0
        stack = null
        breakPoints.clear()
    }


    fun addSourceBreakpoint(sourcePos: Int) {
        val remotePos = breakpointPosFromSource(sourcePos)
        updateBreakPoint(SQLQuery.ADD_BREAKPOINT, remotePos)
        breakPoints[remotePos] = stack != null
    }

    fun removeSourceBreakpoint(sourcePos: Int) {
        val remotePos = breakpointPosFromSource(sourcePos)
        updateBreakPoint(SQLQuery.DROP_BREAKPOINT, remotePos)
        breakPoints.remove(remotePos)
    }

    fun updateBreakPoint(query: SQLQuery, line: Int) {
        stack?.let { plUpdateStackBreakPoint(it.proc, query, def.oid, line) }
    }

    private fun breakpointPosFromSource(sourcePos: Int): Int = sourcePos - offset

    override fun isDirectory(): Boolean = false

    override fun getPath(): String = "${def.oid}"

    override fun getPresentableName(): String = name

    override fun getFileSystem(): VirtualFileSystem = PlVFS.getInstance()

}