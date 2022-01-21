/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.DGDepartment
import com.intellij.database.remote.jdbc.RemoteConnection
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.first
import com.jetbrains.rd.util.measureTimeMillis
import org.jetbrains.concurrency.runAsync
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 *
 */
class PlExecutor(private val controller: PlController) {

    private val lock: Lock = ReentrantLock()
    private val connection = createConnection()
    private var entryPoint = 0L
    private var session = 0
    private var stepTimeOut = 300

    var lastMessage: Message = Message(Severity.INFO, "Starting")
    private val message = mutableListOf<Message>(lastMessage)

    private fun createConnection(): DatabaseConnection = DatabaseSessionManager.getFacade(
        controller.project, controller.connectionPoint, null, controller.searchPath, true, null, DGDepartment.DEBUGGER
    ).connect().get()

    fun checkExtension() {
        //return executeQuery<PlExtension>(SQLQuery.GET_EXTENSION).none { it.name == "pldbgapi" }
        val res = executeQuery<PlExtension>(SQLQuery.GET_EXTENSION)
        return when (val ext = res.find { it.name == "pldbgapi" }) {
            null -> {
                setError("Extension not found, debugger disabled")
            }
            else -> {
                setInfo("Extension found, version=${ext.version}")
            }
        }
    }

    fun searchCallee(callFunc: List<String>, callValues: List<String>) {

        val schema = if (callFunc.size > 1) callFunc[0] else getDefaultSchema()
        val procedure = if (callFunc.size > 1) callFunc[1] else callFunc[0]

        val functions = executeQuery<PlFunctionArg>(
            SQLQuery.GET_FUNCTION_CALL_ARGS,
            listOf(schema, procedure)
        )
        val plArgs = functions.groupBy {
            it.oid
        }

        if (plArgs.size == 1) {
            entryPoint = plArgs.first().key
            return
        }

        entryPoint = plArgs.filter {
            //Check args length
            val minimalCount = it.value.count { arg -> !arg.default }
            callValues.size >= minimalCount && callValues.size <= it.value.size
        }.filterValues { args ->
            //Map call values
            val namedValues = callValues.mapIndexed { index, s ->
                if (s.contains(":=")) s.split(":=")[1].trim() to s.split(":=")[2].trim()
                else args[index].name to s.trim()
            }
            //Build the test query like SELECT [(cast([value] AS [type]) = [value])] [ AND ...]
            val query = namedValues.joinToString(prefix = "(SELECT (", separator = " AND ", postfix = ")) t") {
                val type = args.find { plFunctionArg -> plFunctionArg.name == it.first }?.type
                "(cast(${it.second} as ${type}) = ${it.second})"
            }
            query.let {
                try {
                    executeQuery<PlBoolean>(
                        SQLQuery.RAW_BOOL,
                        listOf(schema, procedure)
                    ).firstOrNull()?.value ?: false
                } catch (e: Exception) {
                    false
                }
            }
        }.map { it.key }.firstOrNull() ?: entryPoint

        if (entryPoint == 0L) {
            setError("Function not found")
        }
    }

    fun startDebug(databaseConnection: DatabaseConnection) {
        when (executeQuery<PlInt>(
            query = SQLQuery.DEBUG_OID,
            args = listOf("$entryPoint"),
            dc = databaseConnection
        ).firstOrNull()?.value ?: -1) {
            0 -> setInfo("Entering direct debug: entryPoint=$entryPoint\"")
            else -> setError("Failed to start direct debug: entryPoint=$entryPoint")
        }
    }

    fun attachToPort(port: Int) {
        session = executeQuery<PlInt>(SQLQuery.ATTACH_TO_PORT, listOf("$port")).firstOrNull()?.value ?: -1
        when {
            session > 0 -> setInfo("Connected to session =$session\"")
            else -> setError("Failed to attach to port: port=$port")
        }
    }

    fun runStep(cmd: SQLQuery): PlStep? {
        return when (cmd) {
            SQLQuery.STEP_OVER,
            SQLQuery.STEP_INTO,
            SQLQuery.STEP_CONTINUE -> executeQuery<PlStep>(
                cmd,
                listOf("$session"), async = stepTimeOut
            ).firstOrNull()
            else -> {
                setError("Invalid step command: $cmd")
                null
            }
        }
    }

    fun getStack(): List<PlStackFrame> = executeQuery<PlStackFrame>(SQLQuery.GET_STACK, listOf("$session"))

    fun getFunctionDef(oid: Long): PlFunctionDef =
        executeQuery<PlFunctionDef>(SQLQuery.GET_FUNCTION_DEF, listOf("$oid")).first()

    fun getVariables(): List<PlStackVariable> {

        val vars = executeQuery<PlStackVariable>(SQLQuery.GET_RAW_VARIABLES, listOf("$session"))

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
        return executeQuery<PlStackVariable>(SQLQuery.GET_JSON_VARIABLES, listOf(query))
    }

    fun explode(value: PlValue): List<PlValue> {
        if (!value.isArray && value.kind != 'c') {
            throw IllegalArgumentException("Explode not supported for: $value")
        }
        val query = if (value.isArray) String.format(
            SQLQuery.EXPLODE_ARRAY.sql,
            value.name,
            value.value.replace("'", "''"),
            "${value.oid}"
        ) else String.format(
            SQLQuery.EXPLODE_COMPOSITE.sql,
            value.value.replace("'", "''"),
            "${value.oid}"
        )
        return executeQuery<PlValue>(SQLQuery.EXPLODE, listOf(query))
    }

    fun getBreakPoints(): List<PlStackBreakPoint> = executeQuery<PlStackBreakPoint>(
        SQLQuery.LIST_BREAKPOINT,
        listOf("$session")
    )

    fun updateBreakPoint(cmd: SQLQuery, oid: Long, line: Int): Boolean {
        //TODO manage failure
        return when (cmd) {
            SQLQuery.ADD_BREAKPOINT,
            SQLQuery.DROP_BREAKPOINT -> executeQuery<PlBoolean>(
                cmd,
                listOf("$session", "$oid", "$line")
            ).firstOrNull()?.value ?: false
            else -> {
                setError("Invalid Breakpoint command: $cmd")
                false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> executeQuery(
        query: SQLQuery,
        args: List<String> = listOf(),
        dc: DatabaseConnection = connection,
        async: Int = 0,
    ): List<T> {
        lock.withLock {
            query.runCatching {
                val res = if (async > 0) {
                    val async = runAsync {
                        getRowSet(query.producer as Producer<T>, query, dc) {
                            fetch(args)
                        }
                    }.blockingGet(async)
                    async
                } else {
                    val sync = getRowSet(query.producer as Producer<T>, query, dc) {
                        fetch(args)
                    }
                    sync
                }
                return res?: mutableListOf<T>()
            }.onFailure {
                setError("Query failed executed: query=${query.name}, args=$args")
            }.onSuccess {
                setInfo("Query executed: query=${query.name}, args=$args")
            }
            return mutableListOf<T>()
        }
    }

    fun ready(): Boolean = (entryPoint != 0L && session != 0)

    fun setError(msg: String) = addMessage(Message(Severity.ERROR, msg))

    fun setInfo(msg: String) = addMessage(Message(Severity.INFO, msg))

    fun setWarning(msg: String) {
        addMessage(Message(Severity.WARNING, msg))
    }

    private fun addMessage(msg: Message) {
        lastMessage = msg
        message.add(0, lastMessage)
    }

    fun hasError(): Boolean = (lastMessage.severity == Severity.ERROR)

    fun hasWarning(): Boolean = (lastMessage.severity == Severity.WARNING)

    private inline fun <T> getRowSet(
        producer: Producer<T>,
        query: SQLQuery,
        connection: DatabaseConnection,
        builder: RowSet<T>.() -> Unit
    ): List<T> =
        DBRowSet(producer, query, connection, null).apply(builder).values


    enum class Severity(val display: String) {
        ERROR(""),
        WARNING(""),
        INFO(""),
    }

    data class Message(val severity: Severity, val content: String, val cause: Throwable? = null)

}
