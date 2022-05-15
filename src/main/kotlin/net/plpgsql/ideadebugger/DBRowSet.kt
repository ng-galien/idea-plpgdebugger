/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.remote.jdbc.RemoteResultSet
import java.util.*

/**
 * JDBC ResultSet iterator.
 * Execute the statement at startup
 */
class DBIterator<R>(producer: Producer<R>,
                    connection: DatabaseConnection,
                    sql: String): RowIterator<R>(producer) {

    private var empty: Boolean
    private var pos = 0
    private val stmt = connection.remoteConnection.createStatement()
    private val rs: RemoteResultSet?

    init {
        empty = !sql.lowercase().trimStart().startsWith("select")
        rs = if (empty) {
            stmt.execute(sql)
            null
        } else {
            stmt.executeQuery(sql)
        }
    }

    override fun hasNext(): Boolean {
        pos = 0
        return !empty && rs!!.next()
    }

    override fun close() {
        rs?.close()
        stmt.close()
    }

    override fun string(): String {
        pos++
        return rs!!.getString(pos)
    }

    override fun int(): Int {
        pos++
        return rs!!.getInt(pos)
    }

    override fun long(): Long {
        pos++
        return rs!!.getLong(pos)
    }

    override fun date(): Date {
        pos++
        return rs!!.getDate(pos)
    }

    override fun bool(): Boolean {
        pos++
        return rs!!.getBoolean(pos)
    }

    override fun char(): Char {
        pos++
        return rs!!.getString(pos)[0]
    }
}

/**
 * JDBC RowSet
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
        initializers.forEach {
            val statement = connection.remoteConnection.createStatement()
            statement.execute(it)
            statement.close()
        }
        return DBIterator(producer = producer, connection, internalSql)
    }
}

