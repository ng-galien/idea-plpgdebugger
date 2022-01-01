package org.postgres.debugger.breakpoint;

import org.postgres.debugger.PlpgDebugProcess;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;

public class PlpgLineBreakPointHandler extends XBreakpointHandler<XLineBreakpoint<PlpgLineBreakPointProperties>> {

    public PlpgLineBreakPointHandler(PlpgDebugProcess debugProcess) {
        super(PlpgLineBreakPointType.class);
    }

    @Override
    public void registerBreakpoint(@NotNull XLineBreakpoint<PlpgLineBreakPointProperties> breakpoint) {

    }

    @Override
    public void unregisterBreakpoint(@NotNull XLineBreakpoint<PlpgLineBreakPointProperties> breakpoint, boolean temporary) {

    }
}
