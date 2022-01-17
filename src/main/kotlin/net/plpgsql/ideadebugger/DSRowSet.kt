/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.remote.jdbc.RemoteConnection
import com.intellij.database.remote.jdbc.RemoteResultSet
import com.intellij.database.remote.jdbc.RemoteStatement
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.runInEdt
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
    cmd: String,
    private val proc: PlProcess
    ) : AbstractRowSet<R>(producer, cmd) {

    override fun open(): RowIterator<R>? {
        val sql = String.format("SELECT * FROM %s;", path)
        runInEdt { proc.session?.consoleView?.print(sql, ConsoleViewContentType.NORMAL_OUTPUT) }
        return DBIterator(producer = producer, proc.connection, sql)
    }

}

inline fun <T> fetchRowSet(producer: Producer<T>, request: Request, proc: PlProcess, builder: RowSet<T>.() -> Unit): List<T> =
    DBRowSet(producer, request.sql.trimIndent(), proc).apply(builder).values