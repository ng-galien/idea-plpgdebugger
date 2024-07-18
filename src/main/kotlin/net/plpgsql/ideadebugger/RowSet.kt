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

import java.util.*

/**
 * Consumes a RowIterator.
 *
 * @author Alexandre Boyer
 */
fun interface Producer<R> {
    fun consume(rs: RowIterator<R>): R
}

/**
 * Abstract class representing a row iterator.
 *
 * @param R the type of the row
 * @property producer the producer that consumes the row iterator
 */
abstract class RowIterator<R>(private val producer: Producer<R>) : Iterator<R>, AutoCloseable {

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

/**
 * A generic interface representing a row set.
 *
 * @param R the type of each row in the row set
 */
interface RowSet<R> {
    fun open(): RowIterator<R>?
    fun fetch(args: List<String>)
}

/**
 * AbstractRowSet is an abstract class that provides a basic implementation of the RowSet interface.
 * It holds a list of items, a path string, and a producer object.
 *
 * @param R the generic type parameter representing the type of the items in the row set.
 * @param producer the producer object responsible for consuming the RowIterator.
 * @param cmd the command string used to fetch the data.
 */
abstract class AbstractRowSet<R>(protected val producer: Producer<R>, private val cmd: String) : RowSet<R> {

    private val _items: MutableList<R> = mutableListOf()
    protected lateinit var path: String
    val values: List<R>
        get() = _items

    override fun fetch(args: List<String>) {
        path = String.format(cmd, *args.toTypedArray())
        open().use {
            it?.forEach { el -> _items.add(el) }
        }
    }
}
