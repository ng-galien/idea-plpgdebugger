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

import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.Ignore


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

    @Ignore
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
