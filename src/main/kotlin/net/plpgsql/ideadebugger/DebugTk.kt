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
import com.intellij.database.util.ErrorHandler
import com.intellij.database.util.GuardedRef
import com.intellij.database.util.SearchPath
import com.intellij.lang.Language
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
): GuardedRef<DatabaseConnection> {
    return DatabaseSessionManager.getFacade(
        project,
        connectionPoint,
        null,
        searchPath,
        true,
        object : ErrorHandler() {},
        DGDepartment.DEBUGGER
    ).connect()
}

/**
 * Get the call statement from a SQL statement.
 */
fun getCallStatement(statement: SqlStatement): CallDefinition {
    val callElement =
        PsiTreeUtil.findChildrenOfType(statement, SqlFunctionCallExpression::class.java).firstOrNull()
    return CallDefinition(DebugMode.DIRECT, callElement, statement.text)
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






