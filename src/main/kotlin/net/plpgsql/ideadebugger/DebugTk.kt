/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager.getFacade
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.DGDepartment
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.sql.dialects.postgres.PgDialect
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

fun searchFunctionByName(
    connection: DatabaseConnection,
    callable: String,
    callValues: List<String>
): Long? {
    val funcName = (if (!callable.contains('.')) "public.$callable" else callable).split(".")
    val plArgs = plGetFunctionArgs(connection = connection, schema = funcName[0], name = funcName[1]).groupBy {
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
        fetchRowSet<PlBoolean>(
            plBooleanProducer(),
            Request.CUSTOM
        ) {
            run(connection, query)
        }.firstOrNull()?.value ?: false

    }.map { it.key }.firstOrNull()

}

fun getPlLanguage(): Language = PgDialect.INSTANCE

fun plNull(value: String) = (value.uppercase() == "NULL")

