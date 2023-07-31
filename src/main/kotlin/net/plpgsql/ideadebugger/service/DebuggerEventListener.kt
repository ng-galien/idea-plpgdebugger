package net.plpgsql.ideadebugger.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.ProjectLevel
import net.plpgsql.ideadebugger.node.ProcessInfo
import net.plpgsql.ideadebugger.node.SourceFile
import java.util.*

@ProjectLevel
val DEBUGGER_EVENT_TOPIC = Topic.create("Debugger event", DebuggerEventListener::class.java)

fun publishEvent(event: DebuggerEvent) {
    Util.getBus().syncPublisher(DEBUGGER_EVENT_TOPIC).onEvent(event)
}

object Util {
    private val BUS = Objects.requireNonNull(ApplicationManager.getApplication().messageBus)
    fun getBus(): MessageBus = BUS
}

interface DebuggerEventListener {
    fun onEvent(event: DebuggerEvent)
}

sealed interface DebuggerEvent {
    fun project(): Project
}

data class SourceLoaded(val project: Project, val file: SourceFile) : DebuggerEvent {
    override fun project(): Project = project
}

data class ProcessCreated(val project: Project, val processInfo: ProcessInfo) : DebuggerEvent {
    override fun project(): Project = project
}

data class ProcessFinished(val project: Project, val processInfo: ProcessInfo) : DebuggerEvent {
    override fun project(): Project = project
}

//data class ProcessStateChanged(val project: Project, val state: ProcessState) : DebuggerEvent {
//    override fun project(): Project = project
//}



