package net.plpgsql.ideadebugger.run

import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import kotlinx.collections.immutable.toImmutableList
import net.plpgsql.ideadebugger.breakpoint.DBBreakpointProperties
import net.plpgsql.ideadebugger.breakpoint.DBLineBreakpointType

class BreakPointHandler( val process: PlProcess) :
    XBreakpointHandler<XLineBreakpoint<DBBreakpointProperties>>(DBLineBreakpointType::class.java),
    XBreakpointListener<XLineBreakpoint<DBBreakpointProperties>> {


    private val breakpoints: MutableList<XLineBreakpoint<DBBreakpointProperties>> = mutableListOf()

    fun getBreakpoints() = breakpoints.toImmutableList()

    override fun registerBreakpoint(breakpoint: XLineBreakpoint<DBBreakpointProperties>) {
        ensureBreakPoint(process, breakpoint)?.let {
            breakpoints.add(breakpoint)
            if (process.readyToAcceptBreakPoint()) {
                addLineBreakPoint(process, breakpoint)
            }
        }
    }

    override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<DBBreakpointProperties>, temporary: Boolean) {
        ensureBreakPoint(process, breakpoint)?.let {
            breakpoints.remove(breakpoint)
            if (process.readyToAcceptBreakPoint()) {
                deleteLineBreakpoint(process, it)
            }
        }
    }
}