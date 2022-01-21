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


fun getPlLanguage(): Language = PgDialect.INSTANCE

fun plNull(value: String) = (value.uppercase() == "NULL")

fun parseFunctionCall(callExpr: SqlFunctionCallExpression): Pair<List<String>, List<String>> {
    val (func, args) = runReadAction {
        val func = PsiTreeUtil.findChildrenOfAnyType(callExpr, SqlIdentifier::class.java).map {
            it.text.trim()
        }
        val values = PsiTreeUtil.findChildOfType(
            callExpr,
            SqlExpressionList::class.java
        )?.children?.map { it.text.trim() }?.filter { it != "" && it != "," && !it.startsWith("--") }
        Pair(first = func, second = values ?: listOf())
    }
    return Pair(func, args)
}

fun getDefaultSchema(): String = "public"




