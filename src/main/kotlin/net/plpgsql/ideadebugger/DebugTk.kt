/*
 * Copyright (c) 2022. Alexandre Boyer
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
import com.intellij.sql.psi.SqlCodeBlockElement
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlStatement
import com.intellij.sql.psi.SqlToken
import com.intellij.sql.psi.impl.SqlTokenElement
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState
import net.plpgsql.ideadebugger.settings.PlPluginSettings

const val CONSOLE = false
const val SELECT_NULL = "SELECT NULL;"
const val DEFAULT_SCHEMA = "public"
const val DEBUGGER_EXTENSION = "pldbgapi"
const val DEBUGGER_SHARED_LIBRARY = "plugin_debugger"
const val DEBUGGER_SESSION_NAME = "idea_debugger"

enum class DebugMode {
    NONE, DIRECT, INDIRECT, ANONYMOUS
}


fun getPlLanguage(): Language = PgDialect.INSTANCE

fun plNull(value: String) = (value.uppercase() == "NULL")

fun console(msg: String, throwable: Throwable? = null) {
    if (CONSOLE) {
        println(msg)
        throwable?.printStackTrace()
    }
}

fun getSettings(): PlPluginSettings {
    return PlDebuggerSettingsState.getInstance().state
}

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
 *
 */
fun getCallStatement(statement: SqlStatement): CallDefinition {
    statement.children.first().let { firstChild ->
        if (firstChild is SqlTokenElement) {
            if (firstChild.text.lowercase() == "do") {
                //SqlCodeBlockElement
                val codeBlock = PsiTreeUtil.findChildOfType(statement, SqlCodeBlockElement::class.java)
                return CallDefinition(DebugMode.ANONYMOUS, statement, codeBlock?.text ?: "")
            }
        }
    }
    val callElement =
        PsiTreeUtil.findChildrenOfType(statement, SqlFunctionCallExpression::class.java).firstOrNull()
    return CallDefinition(DebugMode.DIRECT, callElement, statement.text)
}

/**
 *
 *@param sql
 */
fun sanitizeQuery(sql: String): String {
    var res = sql.trimIndent().replace("(?m)^\\s+\$", "").removeSuffix(";")
    if (res.lowercase().startsWith("select")) {
        res = String.format("(%s)q", res)
    }
    return res
}

fun unquote(s: String): String = s.removeSurrounding("\"")






