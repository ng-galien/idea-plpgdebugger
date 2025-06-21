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

import java.text.SimpleDateFormat
import java.util.*

/*
 * Copyright (c) 2022. Alexandre Boyer
 */

class TextIterator<R>(producer: Producer<R>, private var del: String, cmd: String) : RowIterator<R>(producer) {

    private var row = Scanner(cmd)
    private var line: Scanner? = null

    override fun hasNext(): Boolean {
        if (row.hasNextLine()) {
            line = Scanner(row.nextLine())
            line?.useDelimiter(del)
            return true
        }
        return false
    }

    override fun bool(): Boolean = if (line?.hasNext() == true) line?.nextBoolean()!! else false

    override fun int(): Int = if (line?.hasNext() == true) line?.nextInt()!! else 0

    override fun char(): Char = if (line?.hasNext() == true) line?.next()?.get(0)!! else Char(0)

    override fun long(): Long = if (line?.hasNext() == true) line?.nextLong()!! else 0

    override fun string(): String = if (line?.hasNext() == true) line?.next()!! else ""

    override fun date(): Date = SimpleDateFormat().parse("")

    override fun close() {
    }

}

class TextRowSet<R>(
    producer: Producer<R>,
    private var del: String
) : AbstractRowSet<R>(producer, "%s") {

    override fun open(): RowIterator<R>? {
        return TextIterator(producer = producer,del,  path)
    }

}

inline fun <T> fetchCSV(producer: Producer<T>, builder: RowSet<T>.() -> Unit): List<T> =
    TextRowSet(producer, ";").apply(builder).values
