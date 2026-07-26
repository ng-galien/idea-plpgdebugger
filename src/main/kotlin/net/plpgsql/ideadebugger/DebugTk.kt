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

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.DGDepartment
import com.intellij.database.util.GuardedRef
import com.intellij.database.util.SearchPath
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlStatement
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState
import net.plpgsql.ideadebugger.settings.PlPluginSettings

const val CONSOLE = false
const val SELECT_NULL = "SELECT NULL;"
const val DEFAULT_SCHEMA = "public"
const val DEBUGGER_EXTENSION = "pldbgapi"
const val DEBUGGER_SHARED_LIBRARY = "plugin_debugger"
const val DEBUGGER_SESSION_NAME = "idea_debugger"

private val connectionLogger = Logger.getInstance("net.plpgsql.ideadebugger.connection")

/**
 * Debug mode.
 * NONE: No debugging.
 * DIRECT: Direct debugging, when the function is called directly from the editor.
 * INDIRECT: Indirect debugging, when the function is called from another function.
 */
enum class DebugMode {
    NONE, DIRECT, INDIRECT
}

/**
 * Get the PL/pgSQL language.
 */
fun getPlLanguage(): Language = PgDialect.INSTANCE

/**
 * Check if a string is null.
 */
fun plNull(value: String) = (value.uppercase() == "NULL")

/**
 * Print a message to the console.
 */
fun console(msg: String, throwable: Throwable? = null) {
    if (CONSOLE) {
        println(msg)
        throwable?.printStackTrace()
    }
}

/**
 * Get the settings.
 */
fun getSettings(): PlPluginSettings {
    return PlDebuggerSettingsState.getInstance().state
}

/**
 * Get the auxiliary connection.
 */
fun getAuxiliaryConnection(
    project: Project,
    connectionPoint: DatabaseConnectionPoint,
    searchPath: SearchPath?
): GuardedRef<DatabaseConnection>? {
    val startedAt = System.nanoTime()
    connectionLogger.info(
        "Requesting auxiliary connection " +
            "(thread=${Thread.currentThread().name}, edt=${ApplicationManager.getApplication().isDispatchThread})"
    )
    return try {
        val facade = DatabaseSessionManager.getFacade(
            project,
            connectionPoint,
            null,
            searchPath,
            true,
            department = DGDepartment.DEBUGGER,
        )
        // DatabaseTools 262 deprecates connect(), but its coroutine replacement deadlocks
        // this debugger flow while the SQL runner is waiting for initRemote(). Keep the
        // synchronous facade contract until JetBrains exposes a safe replacement.
        @Suppress("DEPRECATION")
        facade.runSync { facade.connect() }.also {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            connectionLogger.info("Auxiliary connection acquired in ${elapsedMs}ms")
        }
    } catch (e: ProcessCanceledException) {
        connectionLogger.info("Auxiliary connection request canceled")
        throw e
    } catch (e: Exception) {
        connectionLogger.error("Auxiliary connection request failed", e)
        null
    }
}

/**
 * Get the call statement from a SQL statement.
 */
fun getCallStatement(statement: SqlStatement): CallDefinition {
    return withReadAction {
        val callElement =
            PsiTreeUtil.findChildrenOfType(statement, SqlFunctionCallExpression::class.java).firstOrNull()
        CallDefinition(DebugMode.DIRECT, callElement, statement.text)
    }
}

/**
 * Sanitize the query by removing leading and trailing spaces, and the trailing semicolon.
 *@param sql
 */
fun sanitizeQuery(sql: String): String {
    var res = sql.trimIndent().replace("(?m)^\\s+\$", "").removeSuffix(";")
    if (res.lowercase().startsWith("select")) {
        res = String.format("(%s)q", res)
    }
    return res
}

/**
 * Remove the quotes from a string.
 *@param s
 */
fun unquote(s: String): String = s.removeSurrounding("\"")
