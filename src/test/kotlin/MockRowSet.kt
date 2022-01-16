import kotlinx.datetime.toDatePeriod
import net.plpgsql.ideadebugger.AbstractRowSet
import net.plpgsql.ideadebugger.Producer
import java.text.SimpleDateFormat
import java.util.*

/*
 * Copyright (c) 2022. Alexandre Boyer
 */

class CmdRowSet<R>(producer: Producer<R>, cmd: String) : AbstractRowSet<R>(producer, cmd) {
    private var first = true
    override fun next(): Boolean {
        if (first) {
            first = false
            return true
        }
        return false
    }

    override fun bool(): Boolean = strRequest.toBoolean()

    override fun int(): Int = strRequest.toInt()

    override fun char(): Char = strRequest[0]

    override fun long(): Long = strRequest.toLong()

    override fun string(): String = strRequest

    override fun date(): Date = SimpleDateFormat().parse(strRequest)

    override fun open() {

    }

    override fun close() {

    }
}