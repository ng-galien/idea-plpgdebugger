/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.sql.SqlFileType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import kotlin.properties.Delegates

class PlFile(
    private val stack: XStack,
    private val def: PlFunctionDef,
    stackBreakPoint: List<PlStackBreakPoint>
): LightVirtualFile(
    "${def.schema}.${def.name}[${def.oid}].sql",
    SqlFileType.INSTANCE,
    def.source,
) {

    val oid: Long = def.oid
    val breakPoints: MutableList<Int> = mutableListOf()
    var stackPos: Int = 0
    private val initialBP = stackBreakPoint.filter { it.line != -1 }.map { it.line }.toIntArray()
    private var globalBreakPoint: Boolean = !stackBreakPoint.none { it.line == -1 }
    private var offset: Int by Delegates.notNull()

    init {
        var res = 0;
        def.source.split("\n").forEachIndexed { l, line ->
            if (line.lowercase().startsWith("as \$function\$")) res = l - 1
        }
        offset = res;
    }


    fun getPosition(): XSourcePosition? {
        return XDebuggerUtil.getInstance().createPosition(this, stackPos + offset)
    }

    fun updateBreakPoint() {
        stack.remoteBreakPoints.forEach {
            if (it.line > 0 && !breakPoints.contains(it.line)) {
                plDropStackBreakPoint(stack.proc, oid, it.line)
            } else if (it.line < 0) {
                globalBreakPoint = true
            }
        }

        breakPoints.filter { b ->
            stack.remoteBreakPoints.none { s -> s.line == b }
        }.forEach {
            plAddStackBreakPoint(stack.proc, oid, it)
        }
    }

    fun breakPointPosFromFile(sourcePos: Int): Int = sourcePos - offset

    private fun breakPointPosFromStack(sourcePos: Int): Int = sourcePos + offset

    override fun isDirectory(): Boolean = false

    override fun getPath(): String = "remote"

    override fun getFileSystem(): VirtualFileSystem = PlVFS

}