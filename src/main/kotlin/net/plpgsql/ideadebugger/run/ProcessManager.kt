package net.plpgsql.ideadebugger.run

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import net.plpgsql.ideadebugger.XStack
import net.plpgsql.ideadebugger.breakpoint.DBBreakpointProperties
import net.plpgsql.ideadebugger.command.ApiQuery
import net.plpgsql.ideadebugger.command.EMPTY_ENTRY_POINT
import net.plpgsql.ideadebugger.command.PlExecutor
import net.plpgsql.ideadebugger.vfs.PlVirtualFileSystem
import net.plpgsql.ideadebugger.vfs.searchFileFromOid
import net.plpgsql.ideadebugger.vfs.vfs

fun needToContinueExecution(stack: XStack, process: PlProcess): Boolean {
    return stack.frameList().any {
        isStackIsOnFirstLine(it)
                && asFurtherBreakpoint(it, process)
    }
}

fun isStackIsOnFirstLine(topFrame: XStack.XFrame): Boolean {
    return topFrame.firstTime
}

fun asFurtherBreakpoint(topFrame: XStack.XFrame, process: PlProcess): Boolean {
    return process.filesBreakpointList().any { breakPoint ->
        breakPoint.line > topFrame.file.breakPointPositionToLinePosition(topFrame.stackLine)
    }
}

fun topFrameOfStack(stack: XStack): XStack.XFrame {
    return stack.topFrame as XStack.XFrame
}

fun stepInfoForStack(stack: XStack): PlProcess.StepInfo {
    val frame = topFrameOfStack(stack)
    return PlProcess.StepInfo(frame.getSourceLine() + 1, frame.file.codeRange.second, frame.getSourceRatio())
}

fun syncBreakPoints(process: PlProcess) {
    deleteBreakpointsInDatabase(process)
    setFilesBreakpoints(process)
}

private fun setFilesBreakpoints(process: PlProcess) {
    return process.filesBreakpointList().forEach {
        addLineBreakPoint(process, it)
    }
}


private fun deleteBreakpointsInDatabase(process: PlProcess) {
    process.executor.getDbBreakPoints().forEach { deleteLineBreakpoint(process, it.oid, it.line) }
}

fun deleteLineBreakpoint(process: PlProcess, line: XLineBreakpoint<DBBreakpointProperties>) {
    ensureBreakPoint(process, line)?.let {
        deleteLineBreakpoint(process, it)
    }
}

fun deleteLineBreakpoint(process: PlProcess, p: DBBreakpointProperties): Boolean {
    return deleteLineBreakpoint(process, p.oid, p.dbLine)
}

fun deleteLineBreakpoint(process: PlProcess, oid: Long, line: Int): Boolean {
    return process.executor.updateBreakPoint(ApiQuery.DROP_BREAKPOINT, oid, line)
}

fun addLineBreakPoint(process: PlProcess, line: XLineBreakpoint<DBBreakpointProperties>) {
    ensureBreakPoint(process, line)?.let {
        val success = addLineBreakPoint(process, it)
        process.session.let {
            runInEdt {
                if (success) {
                    it.setBreakpointVerified(line)
                } else {
                    it.setBreakpointInvalid(line, "Can't set breakpoint")
                }
            }
        }
    }
}

fun addLineBreakPoint(process: PlProcess, p: DBBreakpointProperties): Boolean {
    if (p.oid == EMPTY_ENTRY_POINT) {
        return false
    }
    return addLineBreakPoint(process, p.oid, p.dbLine)
}

fun addLineBreakPoint(process: PlProcess, oid: Long, line: Int): Boolean {
    return process.executor.updateBreakPoint(ApiQuery.SET_BREAKPOINT, oid, line)
}

fun ensureBreakPoint(process: PlProcess, breakpoint: XLineBreakpoint<DBBreakpointProperties>):
        DBBreakpointProperties? {
    return breakpoint.properties?.let {
        if (it.oid == 0L) {
            return null
        }
        return it
    }?: run {
        breakPointOid(breakpoint)?.let {
            restoreBreakPointProperty(process.session.project, process.executor, it, breakpoint.line)
        }
    }
}

fun breakPointOid(breakpoint: XLineBreakpoint<DBBreakpointProperties>): Long? {
    val path = breakpoint.fileUrl.removePrefix(PlVirtualFileSystem.PROTOCOL_PREFIX)
    return path.toLongOrNull()
}

fun restoreBreakPointProperty(project: Project, executor: PlExecutor, oid: Long, line: Int): DBBreakpointProperties? {
    return searchFileFromOid(project, executor, oid)?.let { source ->
            DBBreakpointProperties(oid, source.linePositionToBreakPointPosition(line))
    }
}
