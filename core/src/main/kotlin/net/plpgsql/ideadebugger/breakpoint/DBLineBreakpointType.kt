/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.breakpoint

import com.intellij.database.debugger.SqlLineBreakpointType
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import javax.swing.Icon


class DBLineBreakpointType : SqlLineBreakpointType<DBBreakpointProperties>(
    "plpg_line_breakpoint",
    "PL/pg SQL") {

    override fun createBreakpointProperties(file: VirtualFile, line: Int): DBBreakpointProperties {
//        return (file as? PlFunctionSource)?.let {
//            DBBreakpointProperties(it.oid, it.linePositionToBreakPointPosition(line))
//        }?: DBBreakpointProperties(EMPTY_ENTRY_POINT, 0)
        return DBBreakpointProperties(0, 0)
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
//        if (file !is PlFunctionSource) {
//            return false
//        }
//        return (file.codeRange.first < line) && (file.codeRange.second > line)
        return false
    }

    override fun getEditorsProvider(
        breakpoint: XLineBreakpoint<DBBreakpointProperties>,
        project: Project
    ): XDebuggerEditorsProvider? {
        return null
    }

    override fun getEnabledIcon(): Icon {
        return AllIcons.Providers.Postgresql
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider? {
        return super.getEditorsProvider()
    }
}



