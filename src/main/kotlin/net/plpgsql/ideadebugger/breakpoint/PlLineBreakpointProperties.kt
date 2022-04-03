package net.plpgsql.ideadebugger.breakpoint

import com.intellij.database.debugger.SqlLineBreakpointProperties
import com.intellij.openapi.vfs.VirtualFile

class PlLineBreakpointProperties(val file: VirtualFile, val line: Int) : SqlLineBreakpointProperties() {}