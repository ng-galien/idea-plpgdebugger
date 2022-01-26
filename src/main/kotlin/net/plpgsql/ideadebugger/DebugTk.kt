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
import java.nio.charset.Charset
import java.security.MessageDigest

const val SELECT_NULL = "SELECT NULL;"

const val DEBUGGER_EXTENSION = "pldbgapi"

fun getPlLanguage(): Language = PgDialect.INSTANCE

fun md5(data: String): String {
    val digest = MessageDigest.getInstance("MD5")
    val encodedHash = digest.digest(data.toByteArray(Charsets.UTF_8))
    val sb: StringBuilder = StringBuilder(encodedHash.size * 2)
    encodedHash.forEach {
        sb.append( String.format("%02x", it) )
    }
    return sb.toString()
}

fun plNull(value: String) = (value.uppercase() == "NULL")

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

fun getDefaultSchema(): String = "public"




