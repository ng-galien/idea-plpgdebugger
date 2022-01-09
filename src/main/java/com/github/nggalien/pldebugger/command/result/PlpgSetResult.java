package com.github.nggalien.pldebugger.command.result;

import java.util.List;

public class PlpgSetResult extends PlpgDebuggerResult {
    private final List<? extends PlpgDebuggerResult> list;

    public PlpgSetResult(List<? extends PlpgDebuggerResult> list) {
        this.list = list;
    }

    public List<? extends PlpgDebuggerResult> getList() {
        return list;
    }
}
