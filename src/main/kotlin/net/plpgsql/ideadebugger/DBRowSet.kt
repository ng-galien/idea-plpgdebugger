/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.xdebugger.XDebugSession
import java.util.*

/**
 *
 */
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

/**
 *
 */
class DBRowSet<R>(
    producer: Producer<R>,
    cmd: SQLQuery,
    private var connection: DatabaseConnection,
    ) : AbstractRowSet<R>(producer, sanitizeQuery(cmd)) {

    var initializers = mutableListOf<String>()

    override fun open(): RowIterator<R>? {
        initializers.forEach {
            val statement = connection.remoteConnection.createStatement()
            statement.execute(it)
            statement.close()
        }
        val query = String.format("SELECT * FROM %s;", path)
        return DBIterator(producer = producer, connection, query)
    }
}

