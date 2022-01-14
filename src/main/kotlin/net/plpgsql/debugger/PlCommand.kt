/*
 * Copyright (c) 2022. Alexandre Boyer
 */


package net.plpgsql.debugger


import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.remote.jdbc.RemoteResultSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.util.*

private const val CREATE_LISTENER = "pldbg_create_listener()"
private const val OID_DEBUG = "plpgsql_oid_debug(%s)"
private const val STEP_OVER = "pldbg_step_over(%s)"
private const val STEP_INTO = "pldbg_step_into(%s)"
private const val CONTINUE = "pldbg_continue(%s)"
private const val GET_STACK = "pldbg_get_stack(%s)"
private const val ATTACH_TO_PORT = "pldbg_attach_to_port(%s)"

private const val GET_VARIABLES = """
(SELECT
       varclass = 'A' as is_arg,
       linenumber as line,
       t_type.oid as oid,
       t_var.name as name,
       coalesce(t_type.typname, 'unknown') as type,
       coalesce(t_type.typtype, 'b') as kind,
       t_type.typarray = 0 as is_array,
       coalesce(t_sub.typname, 'unknown') as array_type,
       t_var.value as value
FROM pldbg_get_variables(%s) t_var
LEFT JOIN pg_type t_type ON t_var.dtype = t_type.oid
LEFT JOIN pg_type t_sub ON t_type.typelem = t_sub.oid) v
"""

private const val GET_FUNCTION_CALL_ARGS = """
(SELECT t_proc.oid,
       pos,
       t_proc.proargnames[pos],
       t_type.typname,
       pos > (t_proc.pronargs - t_proc.pronargdefaults)
FROM pg_proc t_proc
         JOIN pg_namespace t_namespace ON t_proc.pronamespace = t_namespace.oid,
     unnest(t_proc.proargtypes) WITH ORDINALITY arg(el, pos)
         JOIN pg_type t_type ON el = t_type.oid
WHERE t_namespace.nspname LIKE '%s'
  AND t_proc.proname LIKE '%s'
ORDER BY t_proc.oid, pos) a
"""

private const val GET_FUNCTION_DEF = """
(SELECT t_proc.oid,
       t_namespace.nspname,
       t_proc.proname,
       pg_catalog.pg_get_functiondef(t_proc.oid)
FROM pg_proc t_proc
         JOIN pg_namespace t_namespace on t_proc.pronamespace = t_namespace.oid
WHERE t_proc.oid = %s) f
"""

private const val EXPLODE_ARRAY = """
(SELECT 
       t_arr_type.oid                     AS oid,
       '%s[' || idx || ']'                AS name,
       t_arr_type.typname                 AS type,
       t_arr_type.typtype                 AS kind,
       t_arr_type.typarray = 0            AS is_array,
       coalesce(t_sub.typname, 'unknown') AS array_type,
       arr.val::TEXT                      AS value
FROM jsonb_array_elements('%s'::jsonb) WITH ORDINALITY arr(val, idx)
         JOIN pg_type t_type ON t_type.oid = %s
         JOIN pg_type t_arr_type ON t_type.typelem = t_arr_type.oid
         LEFT JOIN pg_type t_sub ON t_arr_type.typelem = t_sub.oid) f
"""

private const val EXPLODE_COMPOSITE = """
(SELECT 
       t_att_type.oid                                   AS oid,
       t_att.attname                                    AS name,
       t_att_type.typname                               AS type_name,
       t_att_type.typtype                               AS kind,
       t_att_type.typarray = 0                          AS is_array,
       coalesce(t_sub.typname, 'unknown')               AS array_type,
       CASE
         WHEN t_att_type.typarray = 0 THEN
             (SELECT array_agg(json_array)::TEXT FROM jsonb_array_elements(jsonb_extract_path(jsonb.val, t_att.attname)) json_array)
         WHEN t_att_type.typtype = 'b' THEN
             jsonb_extract_path_text(jsonb.val, t_att.attname)
         WHEN t_att_type.typtype = 'c' THEN
             jsonb_populate_record(NULL::custom_type, to_jsonb(jsonb_extract_path(jsonb.val, t_att.attname)))::text
         END                            AS value
FROM pg_type t_type
         JOIN pg_class t_class
              ON t_type.typrelid = t_class.oid
         JOIN pg_attribute t_att
              ON t_att.attrelid = t_class.oid AND t_att.attnum > 0
         JOIN pg_type t_att_type
              ON t_att.atttypid = t_att_type.oid
         LEFT JOIN pg_type t_sub ON t_att_type.typelem = t_sub.oid
         JOIN (SELECT to_jsonb('%s'::%s) val) AS jsonb
              ON TRUE
WHERE t_type.oid = %s) c
"""

private const val GET_JSON = """
(SELECT to_jsonb(row) FROM (SELECT %s::%s) row) j
"""

/*
 RowSet implementation for fetching a Datasource
 */
class DBRowSet<R>(
    producer: Producer<R>,
    private val cmd: String
) :
    AbstractRowSet<R>(producer) {
    private val logger = getLogger<DBRowSet<R>>()

    private var connection: DatabaseConnection? = null
    private var rs: RemoteResultSet? = null
    private var pos: Int = 0

    override fun next(): Boolean {
        pos = 0
        return rs?.next() ?: false
    }


    override fun open() {
        val params = String.format(cmd, *parameters.toTypedArray())
        val sql: String = String.format("SELECT * FROM %s;", params)
        logger.info(sql)
        rs = connection?.remoteConnection?.createStatement()?.executeQuery(sql);
    }

    override fun close() {
        rs?.close()
    }

    fun run(dbc: DatabaseConnection, vararg args: String) {
        connection = dbc
        execute(args.asList())
    }

    override fun string(): String {
        pos++
        return rs?.getString(pos) ?: "empty"
    }

    override fun int(): Int {
        pos++
        return rs?.getInt(pos) ?: 0
    }

    override fun long(): Long {
        pos++
        return rs?.getLong(pos) ?: 0L
    }

    override fun date(): Date {
        pos++
        return rs?.getDate(pos) ?: Date()
    }

    override fun bool(): Boolean {
        pos++
        return rs?.getBoolean(pos) ?: false
    }

    override fun char(): Char {
        pos++
        return rs?.getString(pos)?.get(0) ?: Char(0)
    }

}

inline fun <T> fetchRowSet(producer: Producer<T>, cmd: String, builder: DBRowSet<T>.() -> Unit): List<T> =
    DBRowSet<T>(producer, cmd.trimIndent()).apply(builder).values

data class PlBoolean(val value: Boolean)

fun plBooleanProducer() = Producer<PlBoolean> { PlBoolean(it.bool()) }

data class PlInt(val value: Int)

fun plIntProducer() = Producer<PlInt> { PlInt(it.int()) }

data class PlLong(val value: Long)

fun plLongProducer() = Producer<PlLong> { PlLong(it.long()) }

data class PlString(val value: String)

fun plStringProducer() = Producer<PlString> { PlString(it.string()) }

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
        GET_FUNCTION_CALL_ARGS
    ) {
        run(connection, schema, name)
    }

fun plGetFunctionDef(connection: DatabaseConnection, oid: Long): PlFunctionDef =
    fetchRowSet<PlFunctionDef>(
        plVFunctionDefProducer(),
        GET_FUNCTION_DEF
    ) {
        run(connection, "$oid")
    }.first()

fun plCreateListener(connection: DatabaseConnection): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    CREATE_LISTENER
) {
    run(connection)
}.firstOrNull()?.value

fun plDebugFunction(connection: DatabaseConnection, oid: Long): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    OID_DEBUG
) {
    run(connection, "$oid")
}.firstOrNull()?.value

fun plGetStack(connection: DatabaseConnection, session: Int): List<PlStackFrame> = fetchRowSet<PlStackFrame>(
    plStackProducer(),
    GET_STACK
) {
    run(connection, "$session")
}

fun plAttach(connection: DatabaseConnection, port: Int): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    ATTACH_TO_PORT
) {
    run(connection, "$port")
}.firstOrNull()?.value

fun plStepInto(connection: DatabaseConnection, session: Int): PlStep? = fetchRowSet<PlStep>(
    plStepProducer(),
    STEP_INTO
) {
    run(connection, "$session")
}.firstOrNull()

fun plStepOver(connection: DatabaseConnection, session: Int): PlStep? = fetchRowSet<PlStep>(
    plStepProducer(),
    STEP_OVER
)
{
    run(connection, "$session")
}.firstOrNull()

fun plContinue(connection: DatabaseConnection, session: Int): PlStep? = fetchRowSet<PlStep>(
    plStepProducer(),
    CONTINUE
)
{
    run(connection, "$session")
}.firstOrNull()

private fun plGetStackVariablesInner(connection: DatabaseConnection, session: Int): List<PlStackVariable> =
    fetchRowSet<PlStackVariable>(
        plStackVariableProducer(),
        GET_VARIABLES
    ) {
        run(connection, "$session")
    }

fun plGetStackVariables(connection: DatabaseConnection, session: Int): List<PlStackVariable> {
    val vars = plGetStackVariablesInner(connection, session)
    val query = vars.joinToString(prefix = "(", separator = "\nUNION ALL\n", postfix = ") v") {
        // Fix array type prefixed with underscore and NULL
        val realType = if (it.value.isArray) "${it.value.type.substring(1)}[]" else it.value.type
        var realValue = "('${it.value.value}'::${realType})::TEXT"
        // Transform to jsonb
        if (it.value.isArray || it.value.kind == 'c') {
            realValue = "to_jsonb$realValue"
        }
        if (plNull(it.value.value)) {
            realValue = "'NULL'"
        }
        """
        SELECT 
            ${it.isArg}, 
            ${it.line},
            ${it.value.oid},
            '${it.value.name}',
            '$realType',
            '${it.value.kind}',
            ${it.value.isArray},
            '${it.value.arrayType}',
            $realValue
        """
    }
    return fetchRowSet<PlStackVariable>(
        plStackVariableProducer(),
        query
    ) {
        run(connection)
    }
}


fun plExplodeArray(connection: DatabaseConnection, array: PlValue): List<PlValue> =
    fetchRowSet<PlValue>(
        plValueProducer(),
        EXPLODE_ARRAY
    ) {
        run(connection, array.name, array.value, "${array.oid}")
    }

fun plExplodeComposite(connection: DatabaseConnection, composite: PlValue): List<PlValue> =
    fetchRowSet<PlValue>(
        plValueProducer(),
        EXPLODE_COMPOSITE
    ) {
        run(connection, composite.value, composite.type, "${composite.oid}")
    }

fun plGetJson(connection: DatabaseConnection, session: Int, composite: PlValue): PlJson = fetchRowSet<PlJson>(
    plJsonProducer(),
    GET_JSON
) {
    run(connection, composite.value, composite.type)

}.first()



