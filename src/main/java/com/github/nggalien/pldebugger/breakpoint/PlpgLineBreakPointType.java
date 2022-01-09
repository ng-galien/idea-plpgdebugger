package com.github.nggalien.pldebugger.breakpoint;

import com.intellij.database.debugger.SqlLineBreakpointType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlpgLineBreakPointType extends SqlLineBreakpointType<PlpgLineBreakPointProperties> {


    public PlpgLineBreakPointType() {
        super("plpg_line_breakpoint", "PLpg/SQL Line Point");
    }

    @Override
    public @Nullable PlpgLineBreakPointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
        return new PlpgLineBreakPointProperties();
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        return true;
    }

}
