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

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.plpgsql.ideadebugger.command.PlApiFunctionDef
import net.plpgsql.ideadebugger.vfs.PlFunctionSource
import org.junit.BeforeClass
import org.junit.Test
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Function source test
 * Source code is generated returned by the database procedure pg_catalog.pg_get_functiondef
 * @author Alexandre Boyer
 */
@TestDataPath("\$CONTENT_ROOT/src/test/testData")
@RunWith(Parameterized::class)
class PlFunctionSourceTest(
    private val file: String,
    private val start: Int,
    private val range: Pair<Int, Int>,
) : BasePlatformTestCase() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name= "{index}: {0}")
        fun data() : Collection<Array<Any>> {
            return listOf(
                arrayOf("function_with_declare", 2, 7 to 14),
                arrayOf("function_with_declare_with_comments", 2, 9 to 15),
                arrayOf("function_without_declare", 2, 4 to 9),
                arrayOf("function_without_declare_with_comments", 2, 5 to 10),
            )
        }
        var postgres = getPGContainer("14")

        @JvmStatic
        @BeforeClass
        fun setup() {
            postgres.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            postgres.stop()
        }
    }

    @Test
    fun `test function source`() {
        val sourceCode = getFunctionSource(this, postgres, file)
        val def = PlApiFunctionDef(0, "public", file, sourceCode, sourceCode.md5())
        val plSource = PlFunctionSource(project, def, def.md5)
        Assertions.assertEquals(start, plSource.start)
        Assertions.assertEquals(range, plSource.codeRange)
    }
}
