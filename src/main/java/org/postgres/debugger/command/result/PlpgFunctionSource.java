package org.postgres.debugger.command.result;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PlpgFunctionSource extends PlpgDebuggerResult {

    private final Long oid;
    private final String schema;
    private final String name;
    private final Map<String, String> args;
    private final String definition;
    private final Integer sourceOffset;

    public PlpgFunctionSource(Long oid, String schema, String name, String args, String definition, String partialSource) {
        this.oid = oid;
        this.schema = schema;
        this.name = name;
        this.args = Arrays.stream(args.split(","))
                .map(s -> s.trim().split(" "))
                .filter(strings -> strings.length == 2)
                .collect(Collectors.toMap(
                        strings -> strings[0],
                        strings -> strings[1]));
        this.definition = definition;

        AtomicInteger partialPos = new AtomicInteger(0);

        String refLine = Arrays.stream(partialSource.split("\n")).filter(s -> {
            partialPos.incrementAndGet();
            return !s.isEmpty();
        }).findFirst().orElse(null);

        AtomicInteger sourcePos = new AtomicInteger(0);

        Arrays.stream(this.definition.split("\n")).filter(s -> {
            sourcePos.incrementAndGet();
            return s.equals(refLine);
        }).findFirst().orElse(null);

        this.sourceOffset = sourcePos.get() - partialPos.get() - 1;

    }

    public Long getOid() {
        return oid;
    }

    public String getSchema() {
        return schema;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public String getDefinition() {
        return definition;
    }

    public Integer getSourceOffset() {
        return sourceOffset;
    }
}
