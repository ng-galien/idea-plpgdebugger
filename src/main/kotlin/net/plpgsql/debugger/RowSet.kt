/*
 * Copyright (c) 2022. Alexandre Boyer
 */


package net.plpgsql.debugger

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
    fun execute(args: List<String>)
}

abstract class AbstractRowSet<R>(private val producer: Producer<R>) : RowSet<R> {
    private val _items: MutableList<R> = mutableListOf()
    lateinit var parameters: List<String>
    val values: List<R>
        get() = _items

    override fun execute(args: List<String>) {
        parameters = args
        try {
            open()
            while (next()) {
                val res = producer.consume(this)
                _items.add(res)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            close()
        }
    }
}
