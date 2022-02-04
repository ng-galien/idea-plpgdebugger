/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
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
    cmd: String,
    private var connection: DatabaseConnection,
    private var disableDecoration: Boolean
    ) : AbstractRowSet<R>(producer, cmd) {

    var initializers = mutableListOf<String>()
    lateinit var internalSql: String

    override fun open(): RowIterator<R> {
        internalSql = if (disableDecoration) {
            path
        } else {
            String.format("SELECT * FROM %s;", sanitizeQuery(path))
        }
        console(internalSql)
        initializers.forEach {
            val statement = connection.remoteConnection.createStatement()
            statement.execute(it)
            statement.close()
        }
        return DBIterator(producer = producer, connection, internalSql)
    }
}

