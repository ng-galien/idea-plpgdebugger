/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager.getFacade
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.DGDepartment
import com.intellij.lang.Language
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlExpressionList
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlIdentifier
import com.jetbrains.rd.util.first

fun createDebugConnection(project: Project, connectionPoint: DatabaseConnectionPoint): DatabaseConnection {
    return getFacade(
        project,
        connectionPoint,
        null,
        null,
        true,
        null, DGDepartment.DEBUGGER
    ).connect().get()
}

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

fun searchFunctionByName(
    connection: DatabaseConnection,
    callFunc: List<String>,
    callValues: List<String>
): Long? {
    val schema = if (callFunc.size > 1) callFunc[0] else getDefaultSchema()
    val procedure = if (callFunc.size > 1) callFunc[1] else callFunc[0]
    val plArgs = plGetFunctionArgs(connection = connection, schema = schema, name = procedure).groupBy {
        it.oid
    }
    if (plArgs.size == 1) {
        return plArgs.first().key
    }
    return plArgs.filter {
        //Check args lenght
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
                fetchRowSet<PlBoolean>(
                    plBooleanProducer(),
                    Request.CUSTOM,
                    connection
                ) {
                    fetch(query)
                }.firstOrNull()?.value ?: false
            } catch (e: Exception) {
                false
            }
        }
    }.map { it.key }.firstOrNull()

}

fun getPlLanguage(): Language = PgDialect.INSTANCE

fun plNull(value: String) = (value.uppercase() == "NULL")

