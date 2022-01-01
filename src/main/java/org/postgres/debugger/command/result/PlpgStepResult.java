package org.postgres.debugger.command.result;

public class PlpgStepResult extends PlpgDebuggerResult {
    private final long oid;
    private final int lineNumber;
    private final String targetName;

    public PlpgStepResult(long oid, int lineNumber, String targetName) {
        this.oid = oid;
        this.lineNumber = lineNumber;
        this.targetName = targetName;
    }

    public long getOid() {
        return oid;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getTargetName() {
        return targetName;
    }
}
