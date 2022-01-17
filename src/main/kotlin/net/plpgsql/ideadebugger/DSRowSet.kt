/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.runInEdt
import com.intellij.xdebugger.XDebugSession
import java.util.*


class DBIterator<R>(producer: Producer<R>,
                    connection: DatabaseConnection,
                    sql: String): RowIterator<R>(producer) {

    private var pos = 0
    private val stmt = connection.remoteConnection.createStatement()
    private val rs = stmt.executeQuery(sql)

    override fun hasNext(): Boolean {
        pos = 0
        return rs.next()
    }

    override fun close() {
        rs.close()
        stmt.close()
    }

    override fun string(): String {
        pos++
        return rs.getString(pos)
    }

    override fun int(): Int {
        pos++
        return rs.getInt(pos)
    }

    override fun long(): Long {
        pos++
        return rs.getLong(pos)
    }

    override fun date(): Date {
        pos++
        return rs.getDate(pos)
    }

    override fun bool(): Boolean {
        pos++
        return rs.getBoolean(pos)
    }

    override fun char(): Char {
        pos++
        return rs.getString(pos).get(0)
    }

}


class DBRowSet<R>(
    producer: Producer<R>,
    private var cmd: Request,
    private var connection: DatabaseConnection,
    private var xSession: XDebugSession?
    ) : AbstractRowSet<R>(producer, cmd.sql.trimIndent()) {
    private var query: String = "";

    override fun open(): RowIterator<R>? {
         query = String.format("SELECT * FROM %s;", path)
        return DBIterator(producer = producer, connection, query)
    }

    override fun fetch(vararg args: String) {
        runCatching {
            super.fetch(*args)
        }.onFailure {
            runInEdt { xSession?.consoleView?.print("${cmd.name}:\n$query\n", ConsoleViewContentType.ERROR_OUTPUT) }
        }.onSuccess {
            runInEdt { xSession?.consoleView?.print( "${cmd.name}:\n$query\n\n", ConsoleViewContentType.LOG_INFO_OUTPUT) }
        }
    }

}

inline fun <T> fetchRowSet(producer: Producer<T>, request: Request, connection: DatabaseConnection, builder: RowSet<T>.() -> Unit): List<T> =
    DBRowSet(producer, request, connection, null).apply(builder).values

inline fun <T> fetchRowSet(producer: Producer<T>, request: Request, proc: PlProcess, builder: RowSet<T>.() -> Unit): List<T> =
    DBRowSet(producer, request, proc.connection, proc.session).apply(builder).values