/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
 * This variable represents an empty entry point value.
 *
 * @property EMPTY_ENTRY_POINT The value of the empty entry point.
 */
const val EMPTY_ENTRY_POINT = 0L

/**
 * This variable represents an invalid session value.
 *
 * @property INVALID_SESSION The value of the invalid session.
 */
const val INVALID_SESSION = 0

/**
 * Executes PostgreSQL PL Debugger commands and queries.
 *
 * @property guardedRef The guarded reference to the database connection.
 */
class PlExecutor(private val guardedRef: GuardedRef<DatabaseConnection>): Disposable {

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

    /**
     * Runs a step in the PL/pgSQL API execution.
     *
     * @param step the step to execute, can be one of `ApiQuery.STEP_OVER`, `ApiQuery.STEP_INTO`, or `ApiQuery.STEP_CONTINUE`
     * @return the result of the step execution as a `PlApiStep` object, or `null` if the session is invalid or an invalid step command is provided
     */
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

    /**
     * Retrieves the PL/pgSQL API stack frames.
     *
     * @return a list of `PlApiStackFrame` objects representing the stack frames
     */
    fun getStack(): List<PlApiStackFrame> {
        if (invalidSession()) {
            return listOf()
        }
        return executeQuery(query = ApiQuery.GET_STACK, args = listOf("$plSession"))
    }

    /**
     * Retrieves the PL/pgSQL API function definition with the specified OID.
     *
     * @param oid the OID of the function
     * @return the PL/pgSQL API function definition as a PlApiFunctionDef object, or null if no function is found
     */
    fun getFunctionDef(oid: Long): PlApiFunctionDef? =
        executeQuery<PlApiFunctionDef>(query = ApiQuery.GET_FUNCTION_DEF, args = listOf("$oid")).firstOrNull()

    /**
     * Retrieves the PL/pgSQL API stack variables.
     *
     * @return a list of PlApiStackVariable objects representing the stack variables.
     */
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

    /**
     * Explodes a PL/pgSQL API value.
     *
     * @param value the value to explode
     * @return a list of PL/pgSQL API values
     * @throws IllegalArgumentException if the value is not an array or a composite value
     */
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

    /**
     * Retrieves the list of PL/pgSQL API debug steps.
     *
     * This method returns a list of `PlApiStep` objects representing the breakpoints set in the PL/pgSQL API execution.
     * If the session is invalid, an empty list will be returned.
     *
     * @return a list of `PlApiStep` objects representing the breakpoints
     */
    fun getBreakPoints(): List<PlApiStep>{
        if (invalidSession()) {
            return listOf()
        }
        return executeQuery(query = ApiQuery.LIST_BREAKPOINT, args = listOf("$plSession"))
    }


    /**
     * Sets a global breakpoint for debugging in the PL/pgSQL API.
     *
     * @param oid the object ID of the breakpoint to set
     */
    fun setGlobalBreakPoint(oid: Long) {
        if (invalidSession()) {
            return
        }
        executeQuery<PlApiBoolean>(
            ApiQuery.SET_GLOBAL_BREAKPOINT,
            listOf("$plSession", "$oid")
        )
    }

    /**
     * Sets a global breakpoint for debugging.
     */
    fun setGlobalBreakPoint() {
        setGlobalBreakPoint(entryPoint)
    }

    /**
     * Updates the breakpoint for a given command, object ID, and line number.
     *
     * @param cmd the API command representing the breakpoint action
     * @param oid the object ID
     * @param line the line number
     * @return true if the breakpoint was successfully updated, false otherwise
     */
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

    /**
     * Executes a database query and returns a list of results.
     *
     * @param query the query to execute
     * @param args the arguments to use in the query (default is an empty list)
     * @param dc the database connection to use (default is the internal connection)
     * @param additionalCommand an additional command to include in the query (default is null)
     * @param ignoreError a flag indicating whether to ignore any errors that occur during the query (default is false)
     * @return a list of results of type T
     */
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

    /**
     * Executes a custom command with the given raw SQL.
     *
     * @param rawSql the raw SQL command to execute
     * @param connection the database connection to use (optional, defaults to internalConnection)
     * @return true if the command executed successfully, false otherwise
     */
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

    /**
     * Executes a custom query based on the given `ApiQuery` command.
     *
     * @param cmd the `ApiQuery` command to execute
     * @return the resulting query string
     */
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

    /**
     * Sets the specified message as an information message.
     *
     * @param msg the content of the message
     */
    fun setInfo(msg: String) = addMessage(Message(level = Level.INFO, content = msg))

    /**
     * Sets a command message with CMD level.
     *
     * @param msg the content of the message
     */
    private fun setCmd(msg: String) = addMessage(Message(level = Level.CMD, content = msg))

    /**
     * Sets the debug message with the specified content.
     *
     * @param msg the content of the debug message
     */
    fun setDebug(msg: String) = addMessage(Message(level = Level.DEBUG, content = msg))

    /**
     * Sets the SQL message for logging.
     *
     * @param msg the SQL message to set
     */
    private fun setSQL(msg: String) = addMessage(Message(level = Level.SQL, content = msg))

    /**
     * Sets an error message with the given content, optional cause, and optional hint.
     *
     * @param msg the content of the error message
     * @param thw the cause of the error message (optional)
     * @param hint the hint for the error message (optional)
     */
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

    /**
     * Prints the stack of messages to the console view.
     */
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

    /**
     * Checks if the given log level can be printed based on the settings.
     *
     * @param level the log level to check
     * @return true if the log level can be printed, false otherwise.
     */
    private fun canPrint(level: Level): Boolean = when (level) {
        Level.CMD -> settings.showCmd
        Level.DEBUG -> settings.showDebug
        Level.NOTICE -> settings.showNotice
        Level.INFO -> settings.showInfo
        Level.SQL -> settings.showSQL
        else -> true
    }

    /**
     * Adds a message to the internal message log.
     *
     * @param msg the message to add
     */
    private fun addMessage(msg: Message) {
        if (!canPrint(msg.level)) {
            return
        }
        lastMessage = msg
        messages.add(msg)
    }

    /**
     * Checks if there is an error.
     *
     * @return true if there is an error, false otherwise.
     */
    private fun hasError(): Boolean = ready && (lastError != null)

    /**
     * Checks if there is an error and displays an error message.
     * If there is an error, cancels and closes the connection.
     *
     * @return true if there is an error, false otherwise.
     */
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

    /**
     * Cancels the currently executing statement.
     */
    fun cancelStatement() {
        console("cancelConnection")
        if (!internalConnection.remoteConnection.isClosed) {
            console("cancelAll")
            internalConnection.remoteConnection.cancelAll()
        }
    }

    /**
     * Closes the database connection.
     */
    private fun closeConnection() {
        console("cancelConnection")
        if (!internalConnection.remoteConnection.isClosed) {
            console("close")
            internalConnection.remoteConnection.close()
        }
    }

    /**
     * Cancels the statement and closes the connection.
     */
    fun cancelAndCloseConnection() {
        cancelStatement()
        closeConnection()
    }

    /**
     * Retrieves a list of objects of type T from the database.
     *
     * @param producer the producer function that consumes a RowIterator and returns an object of type T
     * @param cmd the SQL command used to retrieve the data
     * @param disableDecoration a flag indicating whether to disable query decoration
     * @param connection the database connection to use
     * @param builder a lambda function that customizes the DBRowSet object
     * @return a list of objects of type T
     */
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


    /**
     * Enum class representing different levels of logging.
     *
     * @property ct the corresponding ConsoleViewContentType for the level
     */
    enum class Level(val ct: ConsoleViewContentType) {
        ERROR(ConsoleViewContentType.ERROR_OUTPUT),
        NOTICE(ConsoleViewContentType.NORMAL_OUTPUT),
        INFO(ConsoleViewContentType.LOG_INFO_OUTPUT),
        CMD(ConsoleViewContentType.LOG_VERBOSE_OUTPUT),
        SQL(ConsoleViewContentType.LOG_WARNING_OUTPUT),
        DEBUG(ConsoleViewContentType.LOG_DEBUG_OUTPUT),
    }

    /**
     * Represents a message with a specific level, content, cause, and hint.
     *
     * @param level the level of the message
     * @param content the content of the message
     * @param cause the cause of the message (optional)
     * @param hint the hint for the message (optional)
     */
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
