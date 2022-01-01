package org.postgres.debugger.command.result;

public class PlpgVariableResult extends PlpgDebuggerResult {

    private final String name;
    private final String varClass;
    private final int lineNumber;
    private final boolean isUnique;
    private final boolean isConst;
    private final boolean isNotNull;
    private final int dType;
    private final String value;
    private final String namedType;

    public PlpgVariableResult(String name,
                              String varClass,
                              int lineNumber,
                              boolean isUnique,
                              boolean isConst,
                              boolean isNotNull,
                              int dType,
                              String value,
                              String namedType) {
        this.name = name;
        this.varClass = varClass;
        this.lineNumber = lineNumber;
        this.isUnique = isUnique;
        this.isConst = isConst;
        this.isNotNull = isNotNull;
        this.dType = dType;
        this.value = value;
        this.namedType = namedType;
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
