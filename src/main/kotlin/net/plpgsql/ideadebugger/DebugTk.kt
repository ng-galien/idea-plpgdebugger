/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.lang.Language
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlExpressionList
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlIdentifier
import com.intellij.sql.psi.SqlReferenceExpression

const val CONSOLE = false
const val SELECT_NULL = "SELECT NULL;"
const val DEFAULT_SCHEMA = "public"
const val DEBUGGER_EXTENSION = "pldbgapi"
const val DEBUGGER_SHARED_LIBRARY = "plugin_debugger"



fun getPlLanguage(): Language = PgDialect.INSTANCE

fun plNull(value: String) = (value.uppercase() == "NULL")

fun console(msg: String, throwable: Throwable? = null) {
    if (CONSOLE) {
        println(msg)
        throwable?.printStackTrace()
    }
}

/**
 * Returns a pair of function definition / arguments
 * @param callExpr
 */
fun parseFunctionCall(callExpr: SqlFunctionCallExpression): Pair<List<String>, List<String>> {
    val (func, args) = runReadAction {
        val funcEl = PsiTreeUtil.findChildOfType(callExpr, SqlReferenceExpression::class.java)
        val func = funcEl?.let {
            PsiTreeUtil.findChildrenOfType(funcEl, SqlIdentifier::class.java).map {
                it.text.trim()
            }
        } ?: listOf()
        val values = PsiTreeUtil.findChildOfType(
            callExpr,
            SqlExpressionList::class.java
        )?.children?.map { it.text.trim() }?.filter { it != "" && it != "," && !it.startsWith("--") }
        Pair(first = func, second = values ?: listOf())
    }
    return Pair(func, args)
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





