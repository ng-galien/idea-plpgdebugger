/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@TestDataPath("\$CONTENT_ROOT/src/test/testData")
@RunWith(Parameterized::class)
class SelectDetectionTest(private val sql: String, private val expected: FunctionDef) : BasePlatformTestCase() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name= "{index}: {0}")
        fun data() : Collection<Array<Any>> {
            return listOf(
                arrayOf("SELECT\n * \nFROM \nfunc();", FunctionDef(DEFAULT_SCHEMA, "func", mapOf())),
                arrayOf("SELECT * FROM func();", FunctionDef(DEFAULT_SCHEMA, "func", mapOf())),
                arrayOf("SELECT func();", FunctionDef(DEFAULT_SCHEMA, "func", mapOf())),
                arrayOf("SELECT sch.func();", FunctionDef("sch", "func", mapOf())),
                arrayOf("SELECT sch.func('arg');", FunctionDef("sch", "func", mapOf("arg_0" to "'arg'"))),
                arrayOf("SELECT sch.func('arg', 123);", FunctionDef("sch", "func", mapOf("arg_0" to "'arg'", "arg_1" to "123"))),
                arrayOf("SELECT sch.func(\n'arg', \n123);", FunctionDef("sch", "func", mapOf("arg_0" to "'arg'", "arg_1" to "123"))),
            )
        }
    }

    @Test
    fun `test run query with select parsing`() {

        val psiFile = createLightFile("dummy.sql", PgDialect.INSTANCE, sql)
        val stmt = psiFile.children.first() as SqlStatement
        val call = getCallStatement(stmt)
        call.parseFunctionCall()
        Assertions.assertNotNull(call)
        Assertions.assertEquals(DebugMode.DIRECT, call.debugMode)
        Assertions.assertEquals(expected.schema, call.schema)
        Assertions.assertEquals(expected.routine, call.routine)
        Assertions.assertEquals(expected.args, call.args)
    }
}