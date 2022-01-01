package org.postgres.debugger.command.result;

public class PlpgLongResult extends PlpgDebuggerResult {
    private final long value;

    public PlpgLongResult(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

}
