/*
 * Copyright (c) 2022. Alexandre Boyer
 */


package net.plpgsql.ideadebugger


import com.intellij.database.dataSource.DatabaseConnection
import org.json.simple.parser.JSONParser


data class PlBoolean(val value: Boolean)

fun plBooleanProducer() = Producer<PlBoolean> { PlBoolean(it.bool()) }

data class PlInt(val value: Int)

fun plIntProducer() = Producer<PlInt> { PlInt(it.int()) }

data class PlLong(val value: Long)

fun plLongProducer() = Producer<PlLong> { PlLong(it.long()) }

data class PlString(val value: String)

public fun plStringProducer() = Producer<PlString> { PlString(it.string()) }

data class PlJson(val value: Any)

fun plJsonProducer() = Producer<PlJson> { PlJson(JSONParser().parse(it.string())) }

data class PlStep(val oid: Long, val line: Int, val target: String)

fun plStepProducer() = Producer<PlStep> { PlStep(it.long(), it.int(), it.string()) }

data class PlStackFrame(val level: Int, val args: String, val oid: Long, val line: Int, val target: String)

fun plStackProducer() = Producer<PlStackFrame> { PlStackFrame(it.int(), it.string(), it.long(), it.int(), it.string()) }

data class PlValue(
    val oid: Long,
    val name: String,
    val type: String,
    val kind: Char,
    val isArray: Boolean,
    val arrayType: String,
    val value: String
)

fun plValueProducer() = Producer<PlValue> {
    PlValue(it.long(), it.string(), it.string(), it.char(), it.bool(), it.string(), it.string())
}

data class PlStackVariable(
    val isArg: Boolean,
    val line: Int,
    val value: PlValue
)

fun plStackVariableProducer() = Producer<PlStackVariable> {
    PlStackVariable(
        it.bool(),
        it.int(),
        PlValue(it.long(), it.string(), it.string(), it.char(), it.bool(), it.string(), it.string())
    )
}

data class PlFunctionArg(val oid: Long, val pos: Int, val name: String, val type: String, val default: Boolean)

fun plFunctionArgProducer() = Producer<PlFunctionArg> {
    PlFunctionArg(
        it.long(),
        it.int(),
        it.string(),
        it.string(),
        it.bool(),
    )
}

data class PlFunctionDef(
    val oid: Long,
    val schema: String,
    val name: String,
    val source: String,
)

fun plVFunctionDefProducer() = Producer<PlFunctionDef> {
    PlFunctionDef(
        it.long(),
        it.string(),
        it.string(),
        it.string()
    )
}

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


fun plGetJson(proc: PlProcess, composite: PlValue): PlJson = fetchRowSet<PlJson>(
    plJsonProducer(),
    Request.T0_JSON,
    proc
) {
    fetch(composite.value, composite.type)
}.first()



