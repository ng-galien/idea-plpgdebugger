package net.plpgsql.ideadebugger

import java.util.*

/*
 * Copyright (c) 2022. Alexandre Boyer
 */


fun interface Producer<R> {
    fun consume(rs: RowIterator<R>): R
}

abstract class RowIterator<R>(private val producer: Producer<R>): Iterator<R>, AutoCloseable {

    abstract fun bool(): Boolean
    abstract fun int(): Int
    abstract fun char(): Char
    abstract fun long(): Long
    abstract fun string(): String
    abstract fun date(): Date
    override fun next(): R {
        return producer.consume(this)
    }
}


interface RowSet<R> {
    fun open(): RowIterator<R>?
    fun fetch(vararg args: String)
}

abstract class AbstractRowSet<R>(protected val producer: Producer<R>, private val cmd: String) : RowSet<R> {

    private val _items: MutableList<R> = mutableListOf()
    protected lateinit var path: String
    val values: List<R>
        get() = _items


    override fun fetch(vararg args: String) {
        path = String.format(cmd, *args)
        runCatching {
            open().use {
                it?.forEach { el -> _items.add(el) }
            }
        }
    }
}

