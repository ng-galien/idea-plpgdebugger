package net.plpgsql.ideadebugger



sealed interface DebuggerItem

data class FilesRoot(val title: String) : DebuggerItem

data class FileItem(val PluginFile: PluginFile) : DebuggerItem

//data class ProcessItem(val plProcess: PlProcess, val mode: DebugMode) : DebuggerItem