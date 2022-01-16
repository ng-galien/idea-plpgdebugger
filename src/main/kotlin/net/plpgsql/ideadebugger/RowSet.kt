/*
 * Copyright (c) 2022. Alexandre Boyer
 */


package net.plpgsql.ideadebugger

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.remote.jdbc.RemoteResultSet
import com.intellij.database.remote.jdbc.RemoteStatement
import java.util.*

fun interface Producer<R> {
    fun consume(rs: RowSet<R>): R
}


interface RowSet<R> {
    fun next(): Boolean
    fun bool(): Boolean
    fun int(): Int
    fun char(): Char
    fun long(): Long
    fun string(): String
    fun date(): Date
    fun open()
    fun close()
    fun execute(vararg args: String)
}

abstract class AbstractRowSet<R>(private val producer: Producer<R>, val cmd: String) : RowSet<R> {
    private val _items: MutableList<R> = mutableListOf()
    protected lateinit var strRequest: String
    val values: List<R>
        get() = _items

    override fun execute(vararg args: String) {
        runCatching {
            strRequest = parseCommand(args.asList())
            open()
            while (next()) {
                val res = producer.consume(this)
                _items.add(res)
            }
        }
        close()
    }

    protected fun parseCommand(args: List<String>): String = String.format(cmd, *args.toTypedArray())
}

/*
 RowSet implementation for fetching a Datasource
 */
class DBRowSet<R>(
    producer: Producer<R>,
    cmd: String,
    private val connection: DatabaseConnection,
) :
    AbstractRowSet<R>(producer, cmd) {
    private val logger = getLogger<DBRowSet<R>>()

    private var stmt: RemoteStatement? = null
    private var rs: RemoteResultSet? = null
    private var pos: Int = 0
    private lateinit var sql: String

    override fun next(): Boolean {
        pos = 0
        return rs?.next() ?: false
    }

    override fun open() {
        sql = String.format("SELECT * FROM %s;", strRequest)
        logger.info(sql)
        rs = connection.runCatching {
            stmt = connection.remoteConnection.createStatement()
            stmt?.executeQuery(sql)
        }.getOrNull()
    }

    override fun close() {
        rs?.close()
        stmt?.close()
    }

    /*
    fun run(dbc: DatabaseConnection, vararg args: String) {
        connection = dbc
        execute(args.asList())
    }

     */

    override fun string(): String {
        pos++
        return rs?.getString(pos) ?: "empty"
    }

    override fun int(): Int {
        pos++
        return rs?.getInt(pos) ?: 0
    }

    override fun long(): Long {
        pos++
        return rs?.getLong(pos) ?: 0L
    }

    override fun date(): Date {
        pos++
        return rs?.getDate(pos) ?: Date()
    }

    override fun bool(): Boolean {
        pos++
        return rs?.getBoolean(pos) ?: false
    }

    override fun char(): Char {
        pos++
        return rs?.getString(pos)?.get(0) ?: Char(0)
    }

}

inline fun <T> fetchRowSet(producer: Producer<T>, request: Request, connection: DatabaseConnection, builder: RowSet<T>.() -> Unit): List<T> =
    DBRowSet<T>(producer, request.sql.trimIndent(), connection).apply(builder).values
