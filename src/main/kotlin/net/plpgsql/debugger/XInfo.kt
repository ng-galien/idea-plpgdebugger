package net.plpgsql.debugger

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext

class XContext(val process: Process) : XSuspendContext()

class XStack(val ctx: XContext) : XExecutionStack("") {
    override fun getTopFrame(): XStackFrame? {
        TODO("Not yet implemented")
    }

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        TODO("Not yet implemented")
    }

}