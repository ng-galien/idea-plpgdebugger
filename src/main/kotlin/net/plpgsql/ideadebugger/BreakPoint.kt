package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlLineBreakpointProperties
import com.intellij.database.debugger.SqlLineBreakpointType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class LineBreakpointProperties : SqlLineBreakpointProperties() {}

class LineBreakpointType :
    SqlLineBreakpointType<LineBreakpointProperties>("plpg_line_breakpoint", "PLpg/SQL Line Break Point") {

    override fun createBreakpointProperties(file: VirtualFile, line: Int): LineBreakpointProperties? {
        return LineBreakpointProperties()
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        return file.extension == "sql"
    }
}

