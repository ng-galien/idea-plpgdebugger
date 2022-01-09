package com.github.nggalien.pldebugger.command.result;

import org.jetbrains.annotations.NotNull;

public class PlpgVariableResult extends PlpgDebuggerResult {

    public final static String UNKNOWN_TYPE = "Unknown";
    private final String name;
    private final String varClass;
    private final int lineNumber;
    private final boolean isUnique;
    private final boolean isConst;
    private final boolean isNotNull;
    private final int dType;
    private final String value;
    private final String namedType;

    public PlpgVariableResult(@NotNull String name,
                              @NotNull String varClass,
                              @NotNull int lineNumber,
                              @NotNull boolean isUnique,
                              @NotNull boolean isConst,
                              @NotNull boolean isNotNull,
                              @NotNull int dType,
                              @NotNull String value,
                              String namedType) {
        this.name = name;
        this.varClass = varClass;
        this.lineNumber = lineNumber;
        this.isUnique = isUnique;
        this.isConst = isConst;
        this.isNotNull = isNotNull;
        this.dType = dType;
        this.value = value;
        this.namedType = dType != 0 ? namedType : UNKNOWN_TYPE;
    }

    public String getName() {
        return name;
    }

    public String getVarClass() {
        return varClass;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public boolean isUnique() {
        return isUnique;
    }

    public boolean isConst() {
        return isConst;
    }

    public boolean isNotNull() {
        return isNotNull;
    }

    public int getdType() {
        return dType;
    }

    public String getValue() {
        return value;
    }

    public String getNamedType() {
        return namedType;
    }
}
