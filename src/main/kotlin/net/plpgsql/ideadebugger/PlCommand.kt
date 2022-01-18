/*
 * Copyright (c) 2022. Alexandre Boyer
 */


package net.plpgsql.ideadebugger


import com.intellij.database.dataSource.DatabaseConnection
import org.json.simple.parser.JSONParser


fun plGetFunctionArgs(connection: DatabaseConnection, name: String, schema: String): List<PlFunctionArg> =
    fetchRowSet<PlFunctionArg>(
        plFunctionArgProducer(),
        Request.GET_FUNCTION_CALL_ARGS,
        connection
    ) {
        fetch(schema, name)
    }

fun plGetFunctionDef(proc: PlProcess, oid: Long): PlFunctionDef =
    fetchRowSet<PlFunctionDef>(
        plVFunctionDefProducer(),
        Request.GET_FUNCTION_DEF,
        proc
    ) {
        fetch("$oid")
    }.first()

fun plCreateListener(proc: PlProcess): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    Request.CREATE_LISTENER,
    proc
) {
    fetch()
}.firstOrNull()?.value

fun plAbort(proc: PlProcess): List<PlBoolean> = fetchRowSet<PlBoolean>(
    plBooleanProducer(),
    Request.ABORT,
    proc
) {
    fetch("${proc.sessionId}")
}

fun plDebugFunction(connection: DatabaseConnection, oid: Long): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    Request.DEBUG_OID,
    connection
) {
    fetch("$oid")
}.firstOrNull()?.value

fun plGetStack(proc: PlProcess): List<PlStackFrame> = fetchRowSet<PlStackFrame>(
    plStackProducer(),
    Request.GET_STACK,
    proc
) {
    fetch("${proc.sessionId}")
}

fun plAttach(proc: PlProcess): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    Request.ATTACH_TO_PORT,
    proc
) {
    fetch("${proc.debugPort}")
}.firstOrNull()?.value

fun plRunStep(proc: PlProcess, request: Request): PlStep? = fetchRowSet<PlStep>(
    plStepProducer(),
    request,
    proc
) {
    when (request) {
        Request.STEP_INTO,
        Request.STEP_OVER,
        Request.STEP_CONTINUE -> fetch("${proc.sessionId}")
        else -> throw IllegalArgumentException("Invalid Step command: ${request.name}")
    }
}.firstOrNull()

private fun plGetBulkStackVariables(proc: PlProcess): List<PlStackVariable> =
    fetchRowSet<PlStackVariable>(
        plStackVariableProducer(),
        Request.GET_VARIABLES,
        proc
    ) {
        fetch("${proc.sessionId}")
    }

fun plGetStackVariables(proc: PlProcess): List<PlStackVariable> {
    val vars = plGetBulkStackVariables(proc)
    val query = vars.joinToString(prefix = "(", separator = "\nUNION ALL\n", postfix = ") v") {
        // Fix array type prefixed with underscore and NULL
        val realType = if (it.value.isArray) "${it.value.type.substring(1)}[]" else it.value.type
        var realValue = "('${it.value.value.replace("'", "''")}'::${realType})::text"
        // Transform to jsonb
        if (it.value.isArray || it.value.kind == 'c') {
            realValue = "to_json$realValue"
        }
        if (plNull(it.value.value)) {
            realValue = "'NULL'"
        }
        "SELECT ${it.isArg},${it.line},${it.value.oid},'${it.value.name}','$realType','${it.value.kind}',${it.value.isArray},'${it.value.arrayType}',$realValue"
    }
    return fetchRowSet<PlStackVariable>(
        plStackVariableProducer(),
        Request.RAW,
        proc
    ) {
        fetch(query)
    }
}


fun plExplodeArray(proc: PlProcess, value: PlValue): List<PlValue> =
    fetchRowSet<PlValue>(
        plValueProducer(),
        Request.EXPLODE,
        proc
    ) {
        if (!value.isArray && value.kind != 'c') {
            throw IllegalArgumentException("Explode not supported for: $value")
        }
        fetch(
            if (value.isArray) String.format(
                Request.EXPLODE_ARRAY.sql,
                value.name,
                value.value.replace("'", "''"),
                "${value.oid}"
            )
            else String.format(
                Request.EXPLODE_COMPOSITE.sql,
                value.value.replace("'", "''"),
                "${value.oid}"
            )
        )
    }

fun plGetPlStackBreakPoint(proc: PlProcess): List<PlStackBreakPoint> = fetchRowSet(
    plStackBreakPointProducer(),
    Request.LIST_BREAKPOINT,
    proc
) {
    fetch("${proc.sessionId}")
}

fun plAddStackBreakPoint(proc: PlProcess, oid: Long, line: Int): Boolean? = fetchRowSet(
    plBooleanProducer(),
    Request.ADD_BREAKPOINT,
    proc
) {
    fetch("${proc.sessionId}", "$oid", "$line")
}.firstOrNull()?.value

fun plDropStackBreakPoint(proc: PlProcess, oid: Long, line: Int): Boolean? = fetchRowSet(
    plBooleanProducer(),
    Request.DROP_BREAKPOINT,
    proc
) {
    fetch("${proc.sessionId}", "$oid", "$line")
}.firstOrNull()?.value


fun plGetJson(proc: PlProcess, composite: PlValue): PlJson = fetchRowSet<PlJson>(
    plJsonProducer(),
    Request.T0_JSON,
    proc
) {
    fetch(composite.value, composite.type)
}.first()



