/*
 * Copyright (c) 2022. Alexandre Boyer
 */


package net.plpgsql.debugger

import java.util.*

fun interface Producer<R> {
    fun consume(rs: RowSet<R>): R
}



interface RowSet<R> {
    abstract fun next(): Boolean
    abstract fun bool(): Boolean
    abstract fun int(): Int
    abstract fun long(): Long
    abstract fun string(): String
    abstract fun date(): Date
    abstract fun open()
    abstract fun close()
    fun execute(args: List<String>)
}

abstract class AbstractRowSet<R>(private val producer: Producer<R>) : RowSet<R> {
    private val _items: MutableList<R> = mutableListOf()
    lateinit var parameters: List<String>
    val values: List<R>
        get() = _items

    override fun execute(args: List<String>) {
        parameters = args
        open()
        try {
            while (next()) {
                _items.add(producer.consume(this))
            }
        } finally {
            close()
        }
    }
}
