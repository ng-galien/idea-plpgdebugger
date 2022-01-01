package org.postgres.debugger;

import org.postgres.debugger.command.result.PlpgFunctionSource;
import org.postgres.debugger.command.result.PlpgStackResult;
import org.postgres.debugger.command.result.PlpgVariableResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PlpgStackInfo {

    private final int level;
    private final long oid;
    private final String functionName;
    private final String functionSource;
    private final int sourcePosition;
    private final Collection<StackValue> arguments;
    private final Collection<StackValue> variables;

    public PlpgStackInfo(@NotNull PlpgStackResult stackResult,
                         @NotNull PlpgFunctionSource functionInfo,
                         @NotNull List<PlpgVariableResult> variableResults) {
        level = stackResult.getLevel();
        oid = stackResult.getStep().getOid();
        functionName = functionInfo.getName();
        functionSource = functionInfo.getDefinition();
        sourcePosition = stackResult.getStep().getLineNumber()+functionInfo.getSourceOffset();
        arguments = variableResults
                .stream()
                .filter(v -> v.getVarClass().equals("A"))
                .map(v -> new SimpleValue(v.getLineNumber(), v.getName(), v.getNamedType(), v.getValue())).collect(Collectors.toList());
        variables = variableResults
                .stream()
                .filter(v -> v.getVarClass().equals("L"))
                .map(v -> new SimpleValue(v.getLineNumber(), v.getName(), v.getNamedType(), v.getValue())).collect(Collectors.toList());
    }

    public int getLevel() {
        return level;
    }

    public long getOid() {
        return oid;
    }

    public String getFunctionName() {
        return functionName;
    }

    public String getFunctionSource() {
        return functionSource;
    }

    public int getSourcePosition() {
        return sourcePosition;
    }

    public Collection<StackValue> getArguments() {
        return arguments;
    }

    public Collection<StackValue> getVariables() {
        return variables;
    }

    public abstract static class StackValue {
        private final int declaration;
        private final String qualifier;
        private final String type;

        public StackValue(@NotNull Integer declaration, @NotNull String qualifier, @NotNull String type) {
            this.declaration = declaration;
            this.qualifier = qualifier;
            this.type = type;
        }

        public int getDeclaration() {
            return declaration;
        }

        public String getQualifier() {
            return qualifier;
        }

        public String getType() {
            return type;
        }

        public abstract String getValue();

    }

    private static final class SimpleValue extends StackValue {
        private final String value;

        public SimpleValue(int declaration, String name, String type, String value) {
            super(declaration, name, type);
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    private static final class CompositeValue extends StackValue {

        private final Collection<StackValue> members;

        public CompositeValue(int declaration, String qualifier, String type) {
            super(declaration, qualifier, type);
            this.members = new ArrayList<>();
        }

        @Override
        public String getValue() {
            return null;
        }

        public void addMember(StackValue value) {
            members.add(value);
        }

        StackValue getMember(String qualifier) {
            return members.stream()
                    .filter(value -> value.qualifier.equals(qualifier))
                    .findFirst().orElse(null);
        }
    }

}
