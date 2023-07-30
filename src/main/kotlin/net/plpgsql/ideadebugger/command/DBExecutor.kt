/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.command

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.util.GuardedRef
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.Messages
import com.intellij.xdebugger.XDebugSession
import net.plpgsql.ideadebugger.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debugger SQL executor.
 * Executes queries from the debugger API
 */
const val EMPTY_ENTRY_POINT = 0L
const val INVALID_SESSION = 0

class DBExecutor(private val guardedRef: GuardedRef<DatabaseConnection>): Disposable {

    var entryPoint = EMPTY_ENTRY_POINT
    private var ready = true
    var waitingForCompletion = false
    private var lastMessage: Message? = null
    private var lastError: Message? = null
    private val messages = mutableListOf<Message>()
    private var internalConnection: DatabaseConnection = guardedRef.get()
    var xSession: XDebugSession? = null
    private var plSession = INVALID_SESSION
    private val settings = getSettings()
    var waiting = AtomicBoolean(false)

    /**
     * Checks the ability to debug and returns a diagnostic.
     */
    fun checkDebugger(): ConnectionDiagnostic {

        // Run the custom command at startup
        var customCommandOk = !settings.enableCustomCommand
        var customCommandMessage = "Session command is disabled"
        if (settings.enableCustomCommand) {
            customCommandOk = executeCustomCommand(settings.customCommand)
            if (!customCommandOk) {
                customCommandMessage = lastError?.content.toString()
            }
        }

        // Shared library is loaded
        val sharedLibraries = executeQuery<PlApiString>(query = ApiQuery.GET_SHARED_LIBRARIES)

        // Extension is created
        val extensions = executeQuery<PlApiExtension>(query = ApiQuery.GET_EXTENSION)

        // Try to kill died debugger sessions
        var activities = getActivities()
        if (activities.isNotEmpty()) {
            executeQuery<PlApiBoolean>(
                query = ApiQuery.PG_CANCEL,
                args = listOf("${activities.first().pid}"),
                ignoreError = true
            )
            activities = getActivities()
        }

        // Build diagnostic
        return ConnectionDiagnostic(
            customCommandOk = customCommandOk,
            customCommandMessage = customCommandMessage,
            sharedLibraries = sharedLibraries.joinToString(separator = ", ") { it.value },
            sharedLibraryOk = sharedLibraries.any { it.value.lowercase().contains(DEBUGGER_SHARED_LIBRARY) },
            extensions = extensions.joinToString(separator = ", ") { it.name },
            extensionOk = extensions.any { it.name.lowercase().contains(DEBUGGER_EXTENSION) },
            activities = activities,
            activityOk = activities.isEmpty()
        )
    }

    private fun invalidSession(): Boolean = (plSession == INVALID_SESSION)

    private fun getActivities(): List<PlActivity> {
        return executeQuery(
            query = ApiQuery.PG_ACTIVITY,
            args = listOf(),
        )
    }

    fun getCallArgs(schema: String, routine: String): List<PlApiFunctionArg> {
        return executeQuery(
            query = ApiQuery.GET_FUNCTION_CALL_ARGS,
            args = listOf(schema, routine),
        )
    }

    fun testBooleanStatement(statement: String): Boolean {
        return try {
            executeQuery<PlApiBoolean>(
                ApiQuery.RAW_BOOL,
                listOf(statement)
            ).firstOrNull()?.value ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun createListener() {
        plSession = executeQuery<PlApiInt>(
            query = ApiQuery.CREATE_LISTENER
        ).firstOrNull()?.value ?: INVALID_SESSION
    }

    fun waitForTarget(): Int {
        if (invalidSession()) {
            return 0
        }
        waiting.set(true)
        val res = executeQuery<PlApiInt>(
            query = ApiQuery.WAIT_FOR_TARGET,
            args = listOf("$plSession")
        ).firstOrNull()?.value ?: 0
        waiting.set(false)
        return res
    }

    fun abort() {
        if (invalidSession()) {
            return
        }
        executeQuery<PlApiBoolean>(
            query = ApiQuery.ABORT,
            args = listOf("$plSession")
        )
    }

    fun runStep(step: ApiQuery): PlApiStep? {
        if (invalidSession()) {
            return null
        }
        waiting.set(true)
        val res = when (step) {
            ApiQuery.STEP_OVER,
            ApiQuery.STEP_INTO,
            ApiQuery.STEP_CONTINUE -> executeQuery<PlApiStep>(
                query = step,
                args = listOf("$plSession")
            ).firstOrNull()
            else -> {
                setError("Invalid step command: $step")
                null
            }
        }
        waiting.set(false)
        return res
    }

    fun getStack(): List<PlApiStackFrame> {
        if (invalidSession()) {
            return listOf()
        }
        return executeQuery(query = ApiQuery.GET_STACK, args = listOf("$plSession"))
    }

    fun getFunctionDef(oid: Long): PlApiFunctionDef? =
        executeQuery<PlApiFunctionDef>(query = ApiQuery.GET_FUNCTION_DEF, args = listOf("$oid")).firstOrNull()

    fun getVariables(): List<PlApiStackVariable> {
        if (invalidSession()) {
            return listOf()
        }
        val vars = executeQuery<PlApiStackVariable>(ApiQuery.GET_RAW_VARIABLES, listOf("$plSession"))

        if (vars.isEmpty()) return vars

        val query = vars.joinToString(separator = "\nUNION ALL\n", postfix = ";") {
            // Fix array type prefixed with underscore and NULL
            val realType = if (it.value.isText) "text" else it.value.type.replace("record", "text")
            val realValue = "('${it.value.value.replace("'", "''")}'::${realType})"
            var jsonValue: String
            var prettyValue: String
            // Transform to jsonb
            if (it.value.isArray || it.value.kind == 'c') {
                jsonValue = "to_json$realValue::text"
                prettyValue = "jsonb_pretty(to_jsonb$realValue)"
            } else {
                jsonValue = "$realValue::text"
                prettyValue = jsonValue
            }
            if (plNull(it.value.value)) {
                jsonValue = "'NULL'"
                prettyValue = "'NULL'"
            }
            "SELECT ${it.isArg},${it.line},${it.value.oid},'${it.value.name}','${it.value.type}','${it.value.kind}'," +
                    "${it.value.isArray},'${it.value.isText}','${it.value.arrayType}',$jsonValue, $prettyValue"

        }
        return executeQuery(query = ApiQuery.GET_JSON_VARIABLES, args = listOf(query))
    }

    fun explode(value: PlApiValue): List<PlApiValue> {
        if (invalidSession()) {
            return listOf()
        }
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
        return executeQuery(query = ApiQuery.EXPLODE, args = listOf(query), ignoreError = true)
    }

    fun getDbBreakPoints(): List<PlApiStep>{
        if (invalidSession()) {
            return listOf()
        }
        return executeQuery(query = ApiQuery.LIST_BREAKPOINT, args = listOf("$plSession"))
    }


    fun setGlobalBreakPoint(oid: Long) : Boolean {
        if (invalidSession()) {
            return false
        }
        return executeQuery<PlApiBoolean>(
            ApiQuery.SET_GLOBAL_BREAKPOINT,
            listOf("$plSession", "$oid")
        ).firstOrNull()?.value ?: false
    }

    fun setGlobalBreakPoint() {
        if (!setGlobalBreakPoint(entryPoint)) {
            throw IllegalStateException("Failed to set global breakpoint");
        }
    }

    fun updateBreakPoint(cmd: ApiQuery, oid: Long, line: Int): Boolean {
        if (invalidSession()) {
            return false
        }
        return when (cmd) {
            ApiQuery.SET_BREAKPOINT,
            ApiQuery.DROP_BREAKPOINT -> executeQuery<PlApiBoolean>(
                cmd,
                listOf("$plSession", "$oid", "$line")
            ).firstOrNull()?.value ?: false
            else -> {
                setError("Invalid Breakpoint command: $cmd")
                false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> executeQuery(
        query: ApiQuery,
        args: List<String> = listOf(),
        dc: DatabaseConnection = internalConnection,
        additionalCommand: String? = null,
        ignoreError: Boolean = false
    ): List<T> {
        console("executeQuery ${query.name}")
        if (query.print) {
            setCmd("query=${query.name}, args=$args")
        }
        var res: List<T>? = null
        val sqlQuery = customQuery(cmd = query)
        var rowset: DBRowSet<T>? = null
        waitingForCompletion = true
        query.runCatching {
            res = getRowSet(
                producer = query.producer as Producer<T>,
                cmd = sqlQuery,
                connection = dc,
                disableDecoration = query.disableDecoration
            ) {
                rowset = this
                initializers.add("SET CLIENT_ENCODING TO 'UTF8';")
                initializers.add("SET application_name TO $DEBUGGER_SESSION_NAME;")
                additionalCommand?.let {
                    initializers.add(additionalCommand)
                }
                fetch(args)
            }
        }.onFailure { throwable ->
            setError("Query failed ${query.name} ${throwable.message}", throwable)
            ready = ignoreError
        }
        waitingForCompletion = false
        rowset?.let {
            setSQL(it.internalSql)
        }
        return res ?: listOf()
    }

    private fun executeCustomCommand(rawSql: String, connection: DatabaseConnection = internalConnection): Boolean {
        setDebug("Execute session command: rawSQL=$rawSql")
        executeQuery<PlApiVoid>(
            query = ApiQuery.RAW_VOID,
            args = listOf(rawSql),
            dc = connection,
            ignoreError = false
        )
        return lastError == null
    }

    private fun customQuery(cmd: ApiQuery): String {

        if (settings.customQuery) {
            return cmd.sql
        }
        return when (cmd) {
            ApiQuery.GET_FUNCTION_CALL_ARGS -> return settings.queryFuncArgs
            ApiQuery.GET_RAW_VARIABLES -> return settings.queryRawVars
            ApiQuery.EXPLODE_COMPOSITE -> return settings.queryExplodeComposite
            ApiQuery.EXPLODE_ARRAY -> return settings.queryExplodeArray
            else -> cmd.sql
        }
    }

    fun setInfo(msg: String) = addMessage(Message(level = Level.INFO, content = msg))

    private fun setCmd(msg: String) = addMessage(Message(level = Level.CMD, content = msg))

    fun setDebug(msg: String) = addMessage(Message(level = Level.DEBUG, content = msg))

    private fun setSQL(msg: String) = addMessage(Message(level = Level.SQL, content = msg))

    fun setError(msg: String, thw: Throwable? = null, hint: String? = null) {
        val err = Message(
            level = Level.ERROR,
            content = msg,
            cause = thw,
            hint = hint
        )
        addMessage(err)
        lastError = err
    }

    fun printStack() {
        xSession?.consoleView?.let {
            val copy = ArrayList(messages)
            runInEdt {
                copy.forEach {
                    if (canPrint(it.level)) {
                        if (it.cause != null) {
                            xSession?.consoleView?.print(
                                "[${it.level.name}] ${it.content} (${it.cause})\n",
                                it.level.ct
                            )
                        } else {
                            xSession?.consoleView?.print("[${it.level.name}] ${it.content}\n", it.level.ct)
                        }
                    }
                }
            }
            messages.clear()
        }
    }

    private fun canPrint(level: Level): Boolean = when (level) {
        Level.CMD -> settings.showCmd
        Level.DEBUG -> settings.showDebug
        Level.NOTICE -> settings.showNotice
        Level.INFO -> settings.showInfo
        Level.SQL -> settings.showSQL
        else -> true
    }

    private fun addMessage(msg: Message) {
        if (!canPrint(msg.level)) {
            return
        }
        lastMessage = msg
        messages.add(msg)
    }

    private fun hasError(): Boolean = ready && (lastError != null)

    fun interrupted(): Boolean {
        if (hasError()) {
            val msg = messages.firstOrNull {
                it.level == Level.ERROR
            }
            runInEdt {
                Messages.showMessageDialog(
                    xSession?.project,
                    msg?.content ?: "Unknown error",
                    "PostgresSQL Debugger",
                    Messages.getErrorIcon()
                )
            }
            cancelAndCloseConnection()
            return true
        }
        return false
    }

    fun cancelStatement() {
        console("cancelConnection")
        if (!internalConnection.remoteConnection.isClosed) {
            console("cancelAll")
            internalConnection.remoteConnection.cancelAll()
        }
    }

    private fun closeConnection() {
        console("cancelConnection")
        if (!internalConnection.remoteConnection.isClosed) {
            console("close")
            internalConnection.remoteConnection.close()
        }
    }

    fun cancelAndCloseConnection() {
        cancelStatement()
        closeConnection()
    }

    private inline fun <T> getRowSet(
        producer: Producer<T>,
        cmd: String,
        disableDecoration: Boolean,
        connection: DatabaseConnection,
        builder: DBRowSet<T>.() -> Unit
    ): List<T> =
        DBRowSet(
            producer = producer,
            cmd = cmd,
            connection = connection,
            disableDecoration = disableDecoration
        ).apply(builder).values


    enum class Level(val ct: ConsoleViewContentType) {
        ERROR(ConsoleViewContentType.ERROR_OUTPUT),
        NOTICE(ConsoleViewContentType.NORMAL_OUTPUT),
        INFO(ConsoleViewContentType.LOG_INFO_OUTPUT),
        CMD(ConsoleViewContentType.LOG_VERBOSE_OUTPUT),
        SQL(ConsoleViewContentType.LOG_WARNING_OUTPUT),
        DEBUG(ConsoleViewContentType.LOG_DEBUG_OUTPUT),
    }

    data class Message(
        val level: Level,
        val content: String,
        val cause: Throwable? = null,
        val hint: String? = null
    )

    override fun dispose() {
        guardedRef.close()
    }
}
