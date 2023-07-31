package net.plpgsql.ideadebugger.node

import net.plpgsql.ideadebugger.DebugMode


sealed interface DebuggerNode

sealed interface FolderNode : DebuggerNode

data class FilesNode(val name: String = "Files") : FolderNode

data class ProcessesNode(val name: String = "Processes") : FolderNode

data class SourceFile(val name: String, val path: String) : DebuggerNode

data class ProcessInfo(val id: Int, val mode: DebugMode) : DebuggerNode

//data class ProcessState(val processInfo: ProcessInfo, val state: String) : DebuggerNode