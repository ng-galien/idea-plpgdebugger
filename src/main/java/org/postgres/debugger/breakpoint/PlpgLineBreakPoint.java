package org.postgres.debugger.breakpoint;

import com.intellij.database.debugger.SqlLineBreakpointType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlpgLineBreakPoint extends SqlLineBreakpointType<PlpgLineBreakPointProperties> {

    protected PlpgLineBreakPoint(@NotNull String id, @Nls @NotNull String title) {
        super(id, title);
    }

    @Override
    public @Nullable PlpgLineBreakPointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
        return new PlpgLineBreakPointProperties();
    }
}
