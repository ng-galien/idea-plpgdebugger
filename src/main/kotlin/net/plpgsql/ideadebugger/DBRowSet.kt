/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

    private var empty: Boolean = !sql.lowercase().trimStart().startsWith("select")
    private var pos = 0
    private val stmt = connection.remoteConnection.createStatement()
    private val rs: RemoteResultSet? = if (empty) {
        stmt.execute(sql)
        null
    } else {
        stmt.executeQuery(sql)
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

