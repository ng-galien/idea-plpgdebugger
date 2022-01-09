package com.github.nggalien.pldebugger.command.result;

import org.jetbrains.annotations.NotNull;

public class PlpgCompositeInfo extends PlpgDebuggerResult {

    private final String field;
    private final String type;
    private final String value;

    public PlpgCompositeInfo(@NotNull String field, @NotNull String type, String value) {
        this.field = field;
        this.type = type;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
