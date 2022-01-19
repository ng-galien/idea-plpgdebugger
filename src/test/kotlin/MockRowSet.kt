import com.intellij.database.dataSource.DatabaseConnection
import net.plpgsql.ideadebugger.*
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