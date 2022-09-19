import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.plpgsql.ideadebugger.DEFAULT_SCHEMA
import net.plpgsql.ideadebugger.DebugMode
import net.plpgsql.ideadebugger.getCallStatement
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


/*
 * Copyright (c) 2022. Alexandre Boyer
 */

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class FacadeDetection : BasePlatformTestCase() {
    
    @Test
    fun testSelectDetection() {
        val testSuite = mapOf(
            "SELECT\n * \nFROM \nfunc();" to Function(DEFAULT_SCHEMA, "func", mapOf()),
            "SELECT * FROM func();" to Function(DEFAULT_SCHEMA, "func", mapOf()),
            "SELECT func();" to Function(DEFAULT_SCHEMA, "func", mapOf()),
            "SELECT sch.func();" to Function("sch", "func", mapOf()),
            "SELECT sch.func('arg');" to Function("sch", "func", mapOf("arg_0" to "'arg'")),
            "SELECT sch.func('arg', 123);" to Function("sch", "func", mapOf("arg_0" to "'arg'", "arg_1" to "123")),
            "SELECT sch.func(\n'arg', \n123);" to Function("sch", "func", mapOf("arg_0" to "'arg'", "arg_1" to "123")),
        )
        Assertions.assertNotNull(myFixture)

        testSuite.forEach {
            val psiFile = createLightFile("dummy.sql", PgDialect.INSTANCE, it.key)
            val stmt = psiFile.children.first() as SqlStatement
            val call = getCallStatement(stmt)
            call.parseFunctionCall()
            Assertions.assertNotNull(call)
            Assertions.assertEquals(DebugMode.DIRECT, call.debugMode)
            Assertions.assertEquals(it.value.schema, call.schema)
            Assertions.assertEquals(it.value.routine, call.routine)
            Assertions.assertEquals(it.value.args, call.args)
        }
    }

    @Test
    fun testCallDetection() {
        val testSuite = mapOf(
            "CALL\nfunc();" to Function(DEFAULT_SCHEMA, "func", mapOf()),
            "CALL func();" to Function(DEFAULT_SCHEMA, "func", mapOf()),
            "CALL sch.func();" to Function("sch", "func", mapOf()),
            "CALL sch.func('arg');" to Function("sch", "func", mapOf("arg_0" to "'arg'")),
            "CALL sch.func('arg', 123);" to Function("sch", "func", mapOf("arg_0" to "'arg'", "arg_1" to "123")),
            "CALL sch.func(\n'arg', \n123);" to Function("sch", "func", mapOf("arg_0" to "'arg'", "arg_1" to "123")),
        )
        Assertions.assertNotNull(myFixture)

        testSuite.forEach {
            val psiFile = createLightFile("dummy.sql", PgDialect.INSTANCE, it.key)
            val stmt = psiFile.children.first() as SqlStatement
            val call = getCallStatement(stmt)
            call.parseFunctionCall()
            Assertions.assertNotNull(call)
            Assertions.assertEquals(DebugMode.DIRECT, call.debugMode)
            Assertions.assertEquals(it.value.schema, call.schema)
            Assertions.assertEquals(it.value.routine, call.routine)
            Assertions.assertEquals(it.value.args, call.args)
        }
    }

    data class Function(val schema: String, val routine: String, val args: Map<String,  String>)

}