package com.github.nggalien.pldebugger.command.result;

public class PlpgStringResult extends PlpgDebuggerResult {
    private final String value;

    public PlpgStringResult(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
