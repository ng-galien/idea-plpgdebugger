/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.lang.Language
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.dialects.postgres.psi.PgParameterDefinitionImpl
import com.intellij.sql.psi.*
import com.intellij.sql.psi.impl.SqlCreateFunctionStatementImpl
import com.intellij.sql.psi.impl.SqlCreateProcedureStatementImpl
import net.plpgsql.ideadebugger.settings.PlPluginSettings

const val CONSOLE = true
const val SELECT_NULL = "SELECT NULL;"
const val DEFAULT_SCHEMA = "public"
const val DEBUGGER_EXTENSION = "pldbgapi"
const val DEBUGGER_SHARED_LIBRARY = "plugin_debugger"

enum class DebugMode {
    NONE, DIRECT, INDIRECT
}


fun getPlLanguage(): Language = PgDialect.INSTANCE

fun plNull(value: String) = (value.uppercase() == "NULL")

fun console(msg: String, throwable: Throwable? = null) {
    if (CONSOLE) {
        println(msg)
        throwable?.printStackTrace()
    }
}

fun getCallStatement(statement: SqlStatement, settings: PlPluginSettings): Pair<DebugMode, PsiElement?> {
    return when (statement) {
        is SqlCreateProcedureStatement -> {
            if (settings.enableIndirect){
                Pair(DebugMode.INDIRECT, statement)
            } else {
                Pair(DebugMode.NONE, null)
            }
        }
        else -> {
            val callElement = PsiTreeUtil.findChildrenOfType(statement, SqlFunctionCallExpression::class.java).firstOrNull()
            Pair(DebugMode.DIRECT, callElement)
        }
    }
}

/**
 * Returns a pair of function definition / arguments
 * @param psi
 */
fun parseFunctionCall(psi: PsiElement, mode: DebugMode): Pair<List<String>, List<String>> {
    val (func, args) = runReadAction {
        val funcEl = PsiTreeUtil.findChildOfType(psi, SqlReferenceExpression::class.java)
        val func = funcEl?.let {
            PsiTreeUtil.findChildrenOfType(funcEl, SqlIdentifier::class.java).map {
                it.text.trim()
            }
        } ?: listOf()
        val values = if (mode == DebugMode.DIRECT) PsiTreeUtil.findChildOfType(
            psi,
            SqlExpressionList::class.java
        )?.children?.map {
            it.text.trim()
        }?.filter {
            it != "" && it != "," && !it.startsWith("--")
        }
        else PsiTreeUtil.findChildrenOfType(psi, PgParameterDefinitionImpl::class.java).mapNotNull { p ->
            PsiTreeUtil.findChildOfType(p, SqlIdentifier::class.java)?.text
        }

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





