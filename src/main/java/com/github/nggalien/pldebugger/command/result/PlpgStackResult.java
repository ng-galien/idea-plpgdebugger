package com.github.nggalien.pldebugger.command.result;

public class PlpgStackResult extends PlpgDebuggerResult {
    private final int level;
    private final String args;
    private final PlpgStepResult step;

    public PlpgStackResult(int level, String args, PlpgStepResult step) {
        this.level = level;
        this.args = args;
        this.step = step;
    }

    public int getLevel() {
        return level;
    }

    public String getArgs() {
        return args;
    }

    public PlpgStepResult getStep() {
        return step;
    }

    public String getVFSName() {
        return getStep().getTargetName() + ".dummy";
    }
}
