/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.Messages
import com.jetbrains.rd.util.first
import java.sql.SQLException

/**
 *
 */
class PlExecutor(private val controller: PlController): Disposable {

    var entryPoint = 0L
    private var ready = true
    var waitingForCompletion = false
    private var lastMessage: Message? = null
    private var lastError: Message? = null
    private val messages = mutableListOf<Message>()

    private var guardedRef = controller.getAuxiliaryConnection()
    private var internalConnection: DatabaseConnection = guardedRef.get()
    private var session = 0

    /**
     * Checks the ability to debug and returns diagnostic
     */
    fun checkDebugger(): ExtensionDiagnostic {

        // Shared library is loaded
        val sharedLibraries = executeQuery<PlApiString>(
            query = ApiQuery.GET_SHARED_LIBRARIES
        )

        // Extension is created
        val extensions = executeQuery<PlApiExtension>(
            query = ApiQuery.GET_EXTENSION
        )

        return ExtensionDiagnostic(
            sharedLibraries = sharedLibraries.joinToString(separator = ", ") { it.value },
            sharedLibraryOk = sharedLibraries.any { it.value == DEBUGGER_SHARED_LIBRARY },
            extensions = extensions.joinToString(separator = ", ") { it.name },
            extensionOk = extensions.any { it.name == DEBUGGER_EXTENSION }
        )
    }

    private fun invalidSession(): Boolean = (session == 0)

    fun searchCallee(callFunc: List<String>, callValues: List<String>, mode: DebugMode) {

        val schema = if (callFunc.size > 1) callFunc[0] else DEFAULT_SCHEMA
        val procedure = if (callFunc.size > 1) callFunc[1] else callFunc[0]

        if (controller.settings.failDetection) {
            setError("[FAKE]Function not found: schema=$schema, name=$procedure")
            return
        }

        val functions = executeQuery<PlApiFunctionArg>(
            query = ApiQuery.GET_FUNCTION_CALL_ARGS,
            args = listOf(schema, procedure),
        ).filter {
            if (callValues.isEmpty()) {
                it.nb == 0 || it.default
            }
            else {
                if (mode==DebugMode.DIRECT) it.nb >= callValues.size else it.nb == callValues.size
            }
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
        if (mode == DebugMode.DIRECT) {
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
        } else {
            entryPoint = plArgs.filter { f ->
                val names = f.value.map { a ->
                    a.name
                }
                if (names.size != callValues.size)
                    false
                val pairList = names.zip(callValues)
                pairList.all { (elt1, elt2) ->
                    elt1 == elt2
                }
            }.map { it.key }.firstOrNull() ?: entryPoint
        }


        if (entryPoint == 0L) {
            setError("Function not found: schema=$schema, name=$procedure")
        }
    }

    fun createListener() {
        session = executeQuery<PlApiInt>(
            query = ApiQuery.CREATE_LISTENER
        ).first().value
    }

    fun waitForTarget(): Int {
        if (invalidSession()) {
            return 0
        }
        return executeQuery<PlApiInt>(
            query = ApiQuery.WAIT_FOR_TARGET,
            args = listOf("$session")
        ).firstOrNull()?.value ?: 0
    }

    private fun abort() {
        if (invalidSession()) {
            return
        }
        executeQuery<PlApiBoolean>(
            query = ApiQuery.ABORT,
            args = listOf("$session")
        )
    }

    fun runStep(step: ApiQuery): PlApiStep? {
        if (invalidSession()) {
            return null
        }
        return when (step) {
            ApiQuery.STEP_OVER,
            ApiQuery.STEP_INTO,
            ApiQuery.STEP_CONTINUE -> executeQuery<PlApiStep>(
                query = step,
                args = listOf("$session")
            ).firstOrNull()
            else -> {
                setError("Invalid step command: $step")
                null
            }
        }
    }

    fun getStack(): List<PlApiStackFrame> {
        if (invalidSession()) {
            return listOf()
        }
        return executeQuery(query = ApiQuery.GET_STACK, args = listOf("$session"))
    }

    fun getFunctionDef(oid: Long): PlApiFunctionDef =
        executeQuery<PlApiFunctionDef>(query = ApiQuery.GET_FUNCTION_DEF, args = listOf("$oid")).first()

    fun getVariables(): List<PlApiStackVariable> {
        if (invalidSession()) {
            return listOf()
        }
        val vars = executeQuery<PlApiStackVariable>(ApiQuery.GET_RAW_VARIABLES, listOf("$session"))

        if (vars.isEmpty()) return vars

        val query = vars.joinToString(separator = "\nUNION ALL\n", postfix = ";") {
            // Fix array type prefixed with underscore and NULL
            val realType = if (it.value.isArray) "${it.value.type.substring(1)}[]" else it.value.type
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
            "SELECT ${it.isArg},${it.line},${it.value.oid},'${it.value.name}','$realType','${it.value.kind}'," +
                    "${it.value.isArray},'${it.value.arrayType}',$jsonValue, $prettyValue"

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
        return executeQuery(ApiQuery.EXPLODE, listOf(query))
    }

    fun getBreakPoints(): List<PlApiStep>{
        if (invalidSession()) {
            return listOf()
        }
        return executeQuery(query = ApiQuery.LIST_BREAKPOINT, args = listOf("$session"))
    }


    fun setGlobalBreakPoint() {
        if (invalidSession()) {
            return
        }
        executeQuery<PlApiBoolean>(
            ApiQuery.SET_GLOBAL_BREAKPOINT,
            listOf("$session", "$entryPoint")
        )
    }

    fun updateBreakPoint(cmd: ApiQuery, oid: Long, line: Int): Boolean {
        if (invalidSession()) {
            return false
        }
        return when (cmd) {
            ApiQuery.SET_BREAKPOINT,
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
        dc: DatabaseConnection = internalConnection,
        additionalCommand: String? = null,
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
                additionalCommand?.let {
                    initializers.add(additionalCommand)
                }

                fetch(args)

            }
        }.onFailure { throwable ->
            when (throwable) {
                is SQLException -> {
                    if ("08006" != throwable.sqlState && "57P01" != throwable.sqlState) {
                        setError("Query failed ${query.name} ${throwable.message}", throwable)
                        println(throwable.message)
                    }
                }
                else -> {
                    setError("Query failed ${query.name} ${throwable.message}", throwable)
                    println(throwable.message)
                }
            }
            ready = false
        }
        waitingForCompletion = false
        rowset?.let {
            setSQL(it.internalSql)
        }
        return res ?: listOf()
    }

    fun executeSessionCommand(rawSql: String, connection: DatabaseConnection = internalConnection) {
        setDebug("Execute session command: rawSQL=$rawSql")
        executeQuery<PlApiVoid>(
            query = ApiQuery.VOID,
            args = listOf(rawSql),
            dc = connection,
            additionalCommand = rawSql
        )
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

    fun ready(): Boolean = ready && session != 0

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
        Level.SQL -> controller.settings.showSQL
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
                    controller.project,
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

    fun cancelConnection() {
        console("cancelConnection")
        if (!internalConnection.remoteConnection.isClosed) {
            console("cancelAll")
            internalConnection.remoteConnection.cancelAll()
            abort()
        }
    }

    fun closeConnection() {
        console("cancelConnection")
        if (!internalConnection.remoteConnection.isClosed) {
            console("cancelAll")
            internalConnection.remoteConnection.cancelAll()
            console("close")
            internalConnection.remoteConnection.close()
        }
    }

    fun cancelAndCloseConnection() {
        cancelConnection()
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
