package com.github.nggalien.pldebugger.breakpoint;

import com.github.nggalien.pldebugger.PlpgDebugProcess;
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
