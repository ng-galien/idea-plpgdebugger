import com.intellij.database.dataSource.DatabaseConnection
import net.plpgsql.ideadebugger.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Copyright (c) 2022. Alexandre Boyer
 */

class RequestTest {

    private inline fun <T> testRowSet(producer: Producer<T>, request: Request, builder: CmdRowSet<T>.() -> Unit): List<T> =
        CmdRowSet<T>(producer, request.sql.trimIndent()).apply(builder).values

    @Test
    fun testCustomRequest() {
        val uuid = "${UUID.randomUUID()}"
        assertEquals(testRowSet(plStringProducer(), Request.CUSTOM) {
            execute(uuid)
        }.first().value, uuid)

    }
}