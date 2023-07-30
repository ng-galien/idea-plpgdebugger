package net.plpgsql.ideadebugger.run

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import net.plpgsql.ideadebugger.DBStack

class ExecContext(val stack: DBStack) : XSuspendContext() {

    override fun getActiveExecutionStack(): XExecutionStack {
        return stack
    }

    override fun getExecutionStacks(): Array<XExecutionStack> {
        return arrayOf(stack)
    }

    override fun computeExecutionStacks(container: XExecutionStackContainer?) {
        container?.addExecutionStack(mutableListOf(stack), true)
    }
}