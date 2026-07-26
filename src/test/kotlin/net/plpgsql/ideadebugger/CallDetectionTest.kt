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


@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class CallDetectionTest : BasePlatformTestCase() {

    fun testCallParsing() {
        val cases = listOf(
            "CALL func();" to FunctionDef(DEFAULT_SCHEMA, "func", mapOf()),
            "CALL sch.func();" to FunctionDef("sch", "func", mapOf()),
            "CALL sch.func('arg');" to FunctionDef("sch", "func", mapOf("arg_0" to "'arg'")),
            "CALL sch.func('arg', 123);" to FunctionDef(
                "sch",
                "func",
                mapOf("arg_0" to "'arg'", "arg_1" to "123"),
            ),
            "CALL sch.func(\n'arg', \n123);" to FunctionDef(
                "sch",
                "func",
                mapOf("arg_0" to "'arg'", "arg_1" to "123"),
            ),
            "CALL sch.func(arg_name := 123);" to FunctionDef(
                "sch",
                "func",
                mapOf("arg_0" to "arg_name := 123"),
            ),
            "CALL sch.func(arg_name => 123);" to FunctionDef(
                "sch",
                "func",
                mapOf("arg_0" to "arg_name => 123"),
            ),
        )

        cases.forEachIndexed { index, (sql, expected) ->
            val psiFile = createLightFile("call-$index.sql", PgDialect.INSTANCE, sql)
            val stmt = psiFile.children.first() as SqlStatement
            val call = getCallStatement(stmt)
            call.parseFunctionCall()
            assertEquals(DebugMode.DIRECT, call.debugMode)
            assertEquals(expected.schema, call.schema)
            assertEquals(expected.routine, call.routine)
            assertEquals(expected.args, call.args)
        }

        assertEquals("arg_name" to "123", parseNamedCallArgument("arg_name := 123"))
        assertEquals("arg_name" to "some_function(1, 2)", parseNamedCallArgument("arg_name => some_function(1, 2)"))
        assertEquals("arg_name" to "some_function(\n  1,\n  2\n)", parseNamedCallArgument("arg_name =>\n some_function(\n  1,\n  2\n)"))
        assertEquals("Quoted Name" to "'value'", parseNamedCallArgument("\"Quoted Name\" := 'value'"))
        assertEquals("Quoted \"Name\"" to "'value'", parseNamedCallArgument("\"Quoted \"\"Name\"\"\" := 'value'"))
        assertNull(parseNamedCallArgument("123"))
    }
}
