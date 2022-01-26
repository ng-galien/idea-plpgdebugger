/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.Messages
import com.jetbrains.rd.util.first
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 *
 */
class PlExecutor(private val controller: PlController) {

    private val lock: Lock = ReentrantLock()
    private var entryPoint = 0L
    private var session = 0
    var interrupted = false

    private var lastMessage: Message? = null

    private var lastError: Message? = null

    private val extensionName = "pldbgapi"

    private val messages = mutableListOf<Message>()

    private val connection = controller.guardedConnection.get()


    fun checkExtension() {
        if (controller.settings.failExtension) {
            setError("[FAKE]Extension $extensionName not found")
            return
        }
        val res = executeQuery<PlApiExtension>(ApiQuery.GET_EXTENSION)
        return when (val ext = res.find { it.name == extensionName }) {
            null -> {
                setError("Extension $extensionName not found")
            }
            else -> {
                setDebug("Extension found, version=${ext.version}")
            }
        }
    }

    fun searchCallee(callFunc: List<String>, callValues: List<String>) {

        val schema = if (callFunc.size > 1) callFunc[0] else getDefaultSchema()
        val procedure = if (callFunc.size > 1) callFunc[1] else callFunc[0]

        if (controller.settings.failDetection) {
            setError("[FAKE]Function not found: schema=$schema, name=$procedure")
            return
        }

        val functions = executeQuery<PlApiFunctionArg>(
            query = ApiQuery.GET_FUNCTION_CALL_ARGS,
            args = listOf(schema, procedure),
        ).filter {
            if (callValues.isEmpty()) it.nb == 0 || it.default else it.nb >= callValues.size
        }

        if (functions.isEmpty()) {
            setError("Function not found: schema=$schema, name=$procedure")
            return
        }

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
                    executeQuery<PlApiBoolean>(
                        ApiQuery.RAW_BOOL,
                        listOf(schema, procedure)
                    ).firstOrNull()?.value ?: false
                } catch (e: Exception) {
                    false
                }
            }
        }.map { it.key }.firstOrNull() ?: entryPoint

        if (entryPoint == 0L) {
            setError("Function not found: schema=$schema, name=$procedure")
        }
    }

    fun startDebug(databaseConnection: DatabaseConnection) {
        if (controller.settings.failStart) {
            setError("[FAKE]Failed to start direct debug: entryPoint=$entryPoint")
            return
        }
        when (executeQuery<PlApiInt>(
            query = ApiQuery.DEBUG_OID,
            args = listOf("$entryPoint"),
            dc = databaseConnection
        ).firstOrNull()?.value ?: -1) {
            0 -> setInfo("Entering direct debug: entryPoint=$entryPoint\"")
            else -> setError("Failed to start direct debug: entryPoint=$entryPoint")
        }
    }

    fun attachToPort(port: Int) {
        if (controller.settings.failAttach) {
            setError("[FAKE]Failed to attach to port: port=$port")
            return
        }
        session = executeQuery<PlApiInt>(
            query = ApiQuery.ATTACH_TO_PORT,
            args = listOf("$port")
        ).firstOrNull()?.value ?: -1
        when {
            session > 0 -> setInfo("Connected to session =$session")
            else -> setError("Failed to attach to port: port=$port")
        }
    }

    fun abort() {
        if (connection.remoteConnection.isClosed) {
            return
        }
        executeQuery<Boolean>(
            query = ApiQuery.ABORT,
            args = listOf("$session")
        )
        terminate()
    }

    fun runStep(cmd: ApiQuery): PlApiStep? {
        return when (cmd) {
            ApiQuery.STEP_OVER,
            ApiQuery.STEP_INTO,
            ApiQuery.STEP_CONTINUE -> executeQuery<PlApiStep>(
                query = cmd,
                args = listOf("$session"),
                interruptible = true
            ).firstOrNull()
            else -> {
                setError("Invalid step command: $cmd")
                null
            }
        }
    }

    fun getStack(): List<PlApiStackFrame> {
        return executeQuery<PlApiStackFrame>(query = ApiQuery.GET_STACK, args = listOf("$session"))
    }

    fun getFunctionDef(oid: Long): PlApiFunctionDef =
        executeQuery<PlApiFunctionDef>(query = ApiQuery.GET_FUNCTION_DEF, args = listOf("$oid")).first()


    fun getVariables(): List<PlApiStackVariable> {

        val vars = executeQuery<PlApiStackVariable>(ApiQuery.GET_RAW_VARIABLES, listOf("$session"))

        if (vars.isEmpty()) return vars;

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
        return executeQuery<PlApiStackVariable>(query = ApiQuery.GET_JSON_VARIABLES, args = listOf(query))
    }

    fun explode(value: PlAiValue): List<PlAiValue> {
        if (!value.isArray && value.kind != 'c') {
            throw IllegalArgumentException("Explode not supported for: $value")
        }
        val query = if (value.isArray) String.format(
            ApiQuery.EXPLODE_ARRAY.sql,
            value.name,
            value.value.replace("'", "''"),
            "${value.oid}"
        ) else String.format(
            ApiQuery.EXPLODE_COMPOSITE.sql,
            value.value.replace("'", "''"),
            "${value.oid}"
        )
        return executeQuery<PlAiValue>(ApiQuery.EXPLODE, listOf(query))
    }

    fun getBreakPoints(): List<PlApiStep> =
        executeQuery<PlApiStep>(query = ApiQuery.LIST_BREAKPOINT, args = listOf("$session"))

    fun updateBreakPoint(cmd: ApiQuery, oid: Long, line: Int): Boolean {
        //TODO manage failure
        return when (cmd) {
            ApiQuery.ADD_BREAKPOINT,
            ApiQuery.DROP_BREAKPOINT -> executeQuery<PlApiBoolean>(
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
        query: ApiQuery,
        args: List<String> = listOf(),
        dc: DatabaseConnection = connection,
        interruptible: Boolean = false,
        skipError: Boolean = false
    ): List<T> {

        if (query.print) {
            setCmd("query=${query.name}, args=$args")
        }
        var res: List<T>? = null
        var error: Throwable? = null
        val sqlQuery = customQuery(cmd = query)
        var rowset: DBRowSet<T>? = null
        lock.withLock {
            query.runCatching {
                res = getRowSet(query.producer as Producer<T>, sqlQuery, dc) {
                    rowset = this
                    initializers.add("SET CLIENT_ENCODING TO 'UTF8'")
                    fetch(args)
                }
            }.onFailure {
                error = it
            }
        }
        error?.let {
            if (!skipError) {
                interrupted = interrupted || interruptible
                setError("Query failed", it)
            } else {
                setDebug("Query failed ${it.message}")
            }
        }
        rowset?.let {
            setDebug(it.internalSql)
        }
        return res ?: listOf<T>()
    }

    private fun customQuery(cmd: ApiQuery): String {
        if (!controller.settings.customQuery) {
            return cmd.sql
        }
        return when (cmd) {
            ApiQuery.GET_FUNCTION_CALL_ARGS -> return controller.settings.queryFuncArgs
            ApiQuery.GET_RAW_VARIABLES -> return controller.settings.queryRawVars
            ApiQuery.EXPLODE_COMPOSITE -> return controller.settings.queryExplodeComposite
            ApiQuery.EXPLODE_ARRAY -> return controller.settings.queryExplodeArray
            else -> cmd.sql
        }
    }


    fun ready(): Boolean {
        return (session != 0)
    }


    private fun Throwable.getRootCause(): Throwable {
        val it = cause
        var res = this
        while (it != res && it != null) {
            res = it
        }
        return res
    }

    fun setInfo(msg: String) = addMessage(Message(level = Level.INFO, content = msg))

    fun setCmd(msg: String) = addMessage(Message(level = Level.CMD, content = msg))

    fun setDebug(msg: String) = addMessage(Message(level = Level.DEBUG, content = msg))

    fun setNotice(msg: String) = addMessage(Message(level = Level.NOTICE, content = msg))

    fun setError(msg: String, thw: Throwable? = null) {
        if (!interrupted) {
            val err = Message(level = Level.ERROR, content = msg, cause = thw?.getRootCause())
            addMessage(err)
            lastError = err
        }
    }

    fun displayInfo() {
        controller.xSession.consoleView?.let {
            val copy = ArrayList(messages)
            runInEdt {
                copy.forEach {
                    if (canPrint(it.level)) {
                        if (it.cause != null) {
                            controller.xSession.consoleView?.print(
                                "[${it.level.name}] ${it.content} (${it.cause})\n",
                                it.level.ct
                            )
                        } else {
                            controller.xSession.consoleView?.print("[${it.level.name}] ${it.content}\n", it.level.ct)
                        }

                    }
                }
            }
            messages.clear()
        }
    }

    private fun canPrint(level: Level): Boolean = when (level) {
        Level.CMD -> controller.settings.showCmd
        Level.DEBUG -> controller.settings.showDebug
        Level.NOTICE -> controller.settings.showNotice
        Level.INFO -> controller.settings.showInfo
        else -> true
    }


    private fun addMessage(msg: Message) {
        if (!canPrint(msg.level)) {
            return
        }
        lastMessage = msg
        messages.add(msg)
    }

    fun hasError(): Boolean = !interrupted && (lastError != null)

    fun interrupted(): Boolean {
        if (hasError()) {
            val msg = messages.firstOrNull {
                it.level == Level.ERROR
            }
            runInEdt {
                Messages.showMessageDialog(
                    controller.project,
                    msg?.content ?: "Unknown error",
                    "PL/pg Debugger",
                    Messages.getErrorIcon()
                )
            }
            terminate()
            return true
        }
        return false
    }

    fun terminate() {
        runCatching {
            interrupted = true
            if (!connection.remoteConnection.isClosed) {
                connection.remoteConnection.close()
            }
        }.onFailure {
            //setError("Terminates with exception", it)
        }.onSuccess {
            setInfo("Terminated without exception")
        }
    }

    private inline fun <T> getRowSet(
        producer: Producer<T>,
        cmd: String,
        connection: DatabaseConnection,
        builder: DBRowSet<T>.() -> Unit
    ): List<T> =
        DBRowSet(producer, cmd, connection).apply(builder).values


    enum class Level(val ct: ConsoleViewContentType) {
        ERROR(ConsoleViewContentType.ERROR_OUTPUT),
        NOTICE(ConsoleViewContentType.NORMAL_OUTPUT),
        INFO(ConsoleViewContentType.LOG_INFO_OUTPUT),
        CMD(ConsoleViewContentType.LOG_VERBOSE_OUTPUT),
        DEBUG(ConsoleViewContentType.LOG_DEBUG_OUTPUT),
    }

    data class Message(
        val level: Level,
        val content: String,
        val cause: Throwable? = null
    )

}
