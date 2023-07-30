package net.plpgsql.ideadebugger.run

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import net.plpgsql.ideadebugger.DBStack
import net.plpgsql.ideadebugger.breakpoint.DBBreakpointProperties
import net.plpgsql.ideadebugger.command.ApiQuery
import net.plpgsql.ideadebugger.command.DBExecutor
import net.plpgsql.ideadebugger.command.EMPTY_ENTRY_POINT
import net.plpgsql.ideadebugger.vfs.PlVirtualFileSystem
import net.plpgsql.ideadebugger.vfs.searchFileFromOid

fun needToContinueExecution(process: PlProcess, stack: DBStack) =
    isStackIsOnFirstLine(topFrameOfStack(stack))
            && asFurtherBreakpoint(process, topFrameOfStack(stack))

fun isStackIsOnFirstLine(frame: DBStack.Frame) = frame.firstTime

fun frameBreakpointList(process: PlProcess, frame: DBStack.Frame) =
    process.filesBreakpointList().filter { breakPoint ->
        breakPointOid(breakPoint)?.let { oid ->
            oid == frame.file.oid
    } ?: false
}

fun asFurtherBreakpoint(process: PlProcess, frame: DBStack.Frame): Boolean {
    return frameBreakpointList(process, frame).any { breakPoint ->
        breakPoint.line > frame.file.breakPointPositionToLinePosition(frame.stackLine)
    }
}

fun topFrameOfStack(stack: DBStack) = stack.topFrame as DBStack.Frame

fun stepInfoForStack(stack: DBStack): PlProcess.StepInfo {
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

private fun deleteBreakpointsInDatabase(process: PlProcess) =
    process.executor.getDbBreakPoints().forEach { deleteLineBreakpoint(process, it.oid, it.line) }

fun deleteLineBreakpoint(process: PlProcess, p: DBBreakpointProperties) =
    deleteLineBreakpoint(process, p.oid, p.dbLine)

fun deleteLineBreakpoint(process: PlProcess, oid: Long, line: Int) =
    process.executor.updateBreakPoint(ApiQuery.DROP_BREAKPOINT, oid, line)

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

fun addLineBreakPoint(process: PlProcess, oid: Long, line: Int) =
    process.executor.updateBreakPoint(ApiQuery.SET_BREAKPOINT, oid, line)

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

fun restoreBreakPointProperty(project: Project, executor: DBExecutor, oid: Long, line: Int): DBBreakpointProperties? {
    return searchFileFromOid(project, executor, oid)?.let { source ->
            DBBreakpointProperties(oid, source.linePositionToBreakPointPosition(line))
    }
}

fun breakPointLineFromStack(process: PlProcess, stack: DBStack): XLineBreakpoint<DBBreakpointProperties>? {
    val topFrame = topFrameOfStack(stack)
    return process.filesBreakpointList().find { breakpoint ->
        breakPointOid(breakpoint)?.let {
            oid -> oid == topFrame.file.oid &&
                breakpoint.line == topFrame.file.breakPointPositionToLinePosition(topFrame.stackLine)
        } ?: false
    }
}
