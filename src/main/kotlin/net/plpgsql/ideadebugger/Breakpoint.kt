/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlLineBreakpointProperties
import com.intellij.database.debugger.SqlLineBreakpointType
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import javax.swing.Icon


class PlLineBreakpointProperties(val file: VirtualFile, val line: Int) : SqlLineBreakpointProperties() {}

/*

 */
class PlLineBreakpointType : SqlLineBreakpointType<PlLineBreakpointProperties>("plpg_line_breakpoint", "PL/pg SQL") {


    override fun createBreakpointProperties(file: VirtualFile, line: Int): PlLineBreakpointProperties {
        return PlLineBreakpointProperties(file, line)
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        if (file !is PlFunctionSource) {
            return false
        }
        return file.codeRange.first < line && file.codeRange.second > line
    }

    override fun getEditorsProvider(
        breakpoint: XLineBreakpoint<PlLineBreakpointProperties>,
        project: Project
    ): XDebuggerEditorsProvider? {
        return null
    }

    override fun getEnabledIcon(): Icon {
        return AllIcons.Providers.Postgresql
    }

}



