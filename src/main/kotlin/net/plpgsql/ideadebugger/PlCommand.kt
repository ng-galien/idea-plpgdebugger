/*
 * Copyright (c) 2022. Alexandre Boyer
 */


package net.plpgsql.ideadebugger

/*
import com.intellij.database.dataSource.DatabaseConnection


fun plGetFunctionArgs(connection: DatabaseConnection, name: String, schema: String): List<PlFunctionArg> =
    fetchRowSet<PlFunctionArg>(
        plFunctionArgProducer(),
        SQLQuery.GET_FUNCTION_CALL_ARGS,
        connection
    ) {
        fetch(schema, name)
    }

fun plGetFunctionDef(proc: PlProcess, oid: Long): PlFunctionDef =
    fetchRowSet<PlFunctionDef>(
        plVFunctionDefProducer(),
        SQLQuery.GET_FUNCTION_DEF,
        proc
    ) {
        fetch("$oid")
    }.first()

fun plCreateListener(proc: PlProcess): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    SQLQuery.CREATE_LISTENER,
    proc
) {
    fetch()
}.firstOrNull()?.value

fun plAbort(proc: PlProcess): List<PlBoolean> = fetchRowSet<PlBoolean>(
    plBooleanProducer(),
    SQLQuery.ABORT,
    proc
) {
    fetch("${proc.sessionId}")
}

fun plDebugFunction(connection: DatabaseConnection, oid: Long): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    SQLQuery.DEBUG_OID,
    connection
) {
    fetch("$oid")
}.firstOrNull()?.value

fun plGetStack(proc: PlProcess): List<PlStackFrame> = fetchRowSet<PlStackFrame>(
    plStackProducer(),
    SQLQuery.GET_STACK,
    proc
) {
    fetch("${proc.sessionId}")
}

fun plAttach(proc: PlProcess): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    SQLQuery.ATTACH_TO_PORT,
    proc
) {
    fetch("${proc.debugPort}")
}.firstOrNull()?.value

fun plRunStep(proc: PlProcess, query: SQLQuery): PlStep? = fetchRowSet<PlStep>(
    plStepProducer(),
    query,
    proc
) {
    when (query) {
        SQLQuery.STEP_INTO,
        SQLQuery.STEP_OVER,
        SQLQuery.STEP_CONTINUE -> fetch("${proc.sessionId}")
        else -> throw IllegalArgumentException("Invalid Step command: ${query.name}")
    }
}.firstOrNull()

private fun plGetBulkStackVariables(proc: PlProcess): List<PlStackVariable> =
    fetchRowSet<PlStackVariable>(
        plStackVariableProducer(),
        SQLQuery.GET_VARIABLES,
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
        SQLQuery.RAW,
        proc
    ) {
        fetch(query)
    }
}

fun plExplodeValue(proc: PlProcess, value: PlValue): List<PlValue> =
    fetchRowSet<PlValue>(
        plValueProducer(),
        SQLQuery.EXPLODE,
        proc
    ) {
        if (!value.isArray && value.kind != 'c') {
            throw IllegalArgumentException("Explode not supported for: $value")
        }
        fetch(
            if (value.isArray) String.format(
                SQLQuery.EXPLODE_ARRAY.sql,
                value.name,
                value.value.replace("'", "''"),
                "${value.oid}"
            )
            else String.format(
                SQLQuery.EXPLODE_COMPOSITE.sql,
                value.value.replace("'", "''"),
                "${value.oid}"
            )
        )
    }

fun plGetPlStackBreakPoint(proc: PlProcess): List<PlStackBreakPoint> = fetchRowSet(
    plStackBreakPointProducer(),
    SQLQuery.LIST_BREAKPOINT,
    proc
) {
    fetch("${proc.sessionId}")
}

fun plUpdateStackBreakPoint(proc: PlProcess, query: SQLQuery, oid: Long, line: Int): Boolean? = fetchRowSet(
    plBooleanProducer(),
    query,
    proc
) {
    when (query) {
        SQLQuery.ADD_BREAKPOINT,
        SQLQuery.DROP_BREAKPOINT -> fetch("${proc.sessionId}", "$oid", "$line")
        else -> throw IllegalArgumentException("Invalid Breakpoint command: ${query.name}")
    }
}.firstOrNull()?.value


fun plGetJson(proc: PlProcess, composite: PlValue): PlJson = fetchRowSet<PlJson>(
    plJsonProducer(),
    SQLQuery.T0_JSON,
    proc
) {
    fetch(composite.value, composite.type)
}.first()

fun plGetShadowList(connection: DatabaseConnection, oids: List<Long>): List<Long> = fetchRowSet<PlLong>(
    plLongProducer(),
    SQLQuery.OLD_FUNCTION,
    connection
) {
    fetch(oids.joinToString(prefix = "ARRAY[", separator = ",", postfix = "]") { "$it" })
}.map { it.value }

fun plExtensionList(connection: DatabaseConnection): List<PlExtension> = fetchRowSet<PlExtension>(
    plExtensionProducer(),
    SQLQuery.GET_EXTENSION,
    connection
) {
    fetch()
}

*/
