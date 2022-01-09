/*
 * Copyright (c) 2022. Alexandre Boyer
 */


package net.plpgsql.debugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.remote.jdbc.RemoteResultSet
import com.intellij.util.containers.toArray
import java.util.*
import kotlin.reflect.KClass

private const val CREATE_LISTENER = "pldbg_create_listener()"
private const val OID_DEBUG = "plpgsql_oid_debug(%s)"
private const val STEP_OVER = "pldbg_step_over(%s)"
private const val STEP_INTO = "pldbg_step_into(%s)"
private const val CONTINUE = "pldbg_continue(%s)"
private const val GET_STACK = "pldbg_get_stack(%s)"
private const val ATTACH_TO_PORT = "pldbg_attach_to_port(%s)"
private const val GET_VARIABLES = """
SELECT name,
       varclass,
       linenumber,
       isunique,
       isconst,
       isnotnull,
       dtype,
       value,
       case
           when dtype > 0 then
                (SELECT t.typname::text FROM pg_type t WHERE t.oid = dtype LIMIT 1)
           ELSE
               'unknown'
           end as typname
FROM pldbg_get_variables(%s)
"""
private const val SEARCH_FUNCTION_INFO = """
SELECT t_proc.oid,
       t_namespace.nspname,
       t_proc.proname,
       pg_catalog.pg_get_function_arguments(t_proc.oid),
       pg_catalog.pg_get_functiondef(t_proc.oid),
       t_proc.prosrc
FROM pg_proc t_proc
         JOIN pg_namespace t_namespace on t_proc.pronamespace = t_namespace.oid
WHERE t_namespace.nspname = '%s'
  AND t_proc.proname = '%s'
"""
private const val GET_FUNCTION_INFO = """
SELECT t_proc.oid,
       t_namespace.nspname,
       t_proc.proname,
       pg_catalog.pg_get_function_arguments(t_proc.oid),
       pg_catalog.pg_get_functiondef(t_proc.oid),
       t_proc.prosrc
FROM pg_proc t_proc
         JOIN pg_namespace t_namespace on t_proc.pronamespace = t_namespace.oid
WHERE t_proc.oid = %d
"""

/*
 RowSet implementation for fetching a Datasource
 */



class DBRowSet<R>(
    producer: Producer<R>,
    private val cmd: String
) :
    AbstractRowSet<R>(producer) {

    private lateinit var connection: DatabaseConnection
    private lateinit var rs: RemoteResultSet
    private var pos: Int = 1

    override fun next(): Boolean {
        pos = 1
        return rs.next()
    }


    override fun open() {
        val params = String.format(cmd, *parameters.toTypedArray())
        val sql: String = String.format("SELECT r.* FROM (%s) r;", params)
        val stmt = connection.remoteConnection.createStatement()
        rs = stmt.executeQuery(sql);
    }

    override fun close() {
        rs.close()
    }

    fun run(dbc: DatabaseConnection, vararg args: String) {
        connection = dbc
        execute(args.asList())
    }

    override fun string(): String {
        pos++
        return rs.getString(pos)
    }

    override fun int(): Int {
        pos++
        return rs.getInt(pos)
    }

    override fun long(): Long {
        pos++
        return rs.getLong(pos)
    }

    override fun date(): Date {
        pos++
        return rs.getDate(pos)
    }

    override fun bool(): Boolean {
        pos++
        return rs.getBoolean(pos)
    }

}

inline fun <T> fetchRowSet(producer: Producer<T>, cmd: String, builder: DBRowSet<T>.() -> Unit): List<T> =
    DBRowSet<T>(producer, cmd).apply(builder).values

data class PlInt(val value: Int)

fun plIntProducer() = Producer<PlInt> { PlInt(it.int()) }

data class PlLong(val value: Long)

fun plLongProducer() = Producer<PlLong> { PlLong(it.long()) }

data class PlString(val value: String)

fun plStringProducer() = Producer<PlString> { PlString(it.string()) }

data class PlStep(val oid: Long, val line: Int, val target: String)

fun plStepProducer() = Producer<PlStep> { PlStep(it.long(), it.int(), it.string()) }

data class PlStack(val level: Int, val args: String, val oid: Long, val line: Int, val target: String)

fun plStackProduction() = Producer<PlStack> { PlStack(it.int(), it.string(), it.long(), it.int(), it.string()) }

data class PlVariable(
    val name: String,
    val kind: String,
    val line: Int,
    val isUnique: Boolean,
    val isConst: Boolean,
    val isNotNull: Boolean,
    val type: String,
    val value: String,
)

fun plVariableProduction() = Producer<PlVariable> {
    PlVariable(
        it.string(),
        it.string(),
        it.int(),
        it.bool(),
        it.bool(),
        it.bool(),
        it.string(),
        it.string(),
    )
}

data class PlFunctionDef(
    val oid: Long,
    val schema: String,
    val name: String,
    val arguments: String,
    val definition: String,
    val source: String,
)

fun plVFunctionDefProducer() = Producer<PlFunctionDef> {
    PlFunctionDef(
        it.long(),
        it.string(),
        it.string(),
        it.string(),
        it.string(),
        it.string(),
    )
}


fun plSearchFunction(connection: DatabaseConnection, name: String, schema: String): List<PlFunctionDef> =
    fetchRowSet<PlFunctionDef>(
        plVFunctionDefProducer(),
        SEARCH_FUNCTION_INFO
    ) {
        run(connection, name, schema)
    }

fun plGetFunction(connection: DatabaseConnection, oid: Long): PlFunctionDef? =
    fetchRowSet<PlFunctionDef>(
        plVFunctionDefProducer(),
        GET_FUNCTION_INFO
    ) {
        run(connection, "$oid")
    }.firstOrNull()

fun plCreateListener(connection: DatabaseConnection): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    CREATE_LISTENER
) {
    run(connection)
}.firstOrNull()?.value

suspend fun plDebug(connection: DatabaseConnection, oid: Long): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    OID_DEBUG
) {
    run(connection, "$oid")
}.firstOrNull()?.value

suspend fun plGetStack(connection: DatabaseConnection, oid: Long): PlStack? = fetchRowSet<PlStack>(
    plStackProduction(),
    GET_STACK
) {
    run(connection, "$oid")
}.firstOrNull()

suspend fun plAttach(connection: DatabaseConnection, port: Int): Int? = fetchRowSet<PlInt>(
    plIntProducer(),
    ATTACH_TO_PORT
) {
    run(connection, "$port")
}.firstOrNull()?.value

suspend fun plStepInto(connection: DatabaseConnection, oid: Long): PlStep? = fetchRowSet<PlStep>(
    plStepProducer(),
    STEP_INTO
) {
    run(connection, "$oid")
}.firstOrNull()

suspend fun plStepOver(connection: DatabaseConnection, oid: Long): PlStep? = fetchRowSet<PlStep>(
    plStepProducer(),
    STEP_OVER
)
{
    run(connection, "$oid")
}.firstOrNull()

fun plContinue(connection: DatabaseConnection, oid: Long): PlStep? = fetchRowSet<PlStep>(
    plStepProducer(),
    CONTINUE
)
{
    run(connection, "$oid")
}.firstOrNull()

fun plGetVariables(connection: DatabaseConnection, port: Int): List<PlVariable> = fetchRowSet<PlVariable>(
    plVariableProduction(),
    GET_VARIABLES
) {
    run(connection, "$port")
}

