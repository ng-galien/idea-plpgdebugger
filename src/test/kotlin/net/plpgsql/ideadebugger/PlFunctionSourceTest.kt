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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.plpgsql.ideadebugger.command.PlApiFunctionDef
import net.plpgsql.ideadebugger.vfs.PlFunctionSource
import java.security.MessageDigest
import java.util.concurrent.Callable

/**
 * Function source test
 * Source code is generated returned by the database procedure pg_catalog.pg_get_functiondef
 * @author Alexandre Boyer
 */
@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class PlFunctionSourceTest : BasePlatformTestCase() {

    fun testFunctionSourceRanges() {
        val cases = listOf(
            Triple("function_with_declare", 2, 7 to 14),
            Triple("function_with_declare_with_comments", 2, 9 to 15),
            Triple("function_without_declare", 2, 4 to 9),
            Triple("function_without_declare_with_comments", 2, 5 to 10),
        )

        cases.forEach { (file, start, range) ->
            val sourceCode = requireNotNull(javaClass.getResource("/$file.sql")) {
                "Missing test resource: $file.sql"
            }.readText().replace("$$", "\$function\$")
            val def = PlApiFunctionDef(0, "public", file, sourceCode, sourceCode.md5())
            val plSource = PlFunctionSource(project, def, def.md5)
            assertEquals(start, plSource.start)
            assertEquals(range, plSource.codeRange)
        }
    }

    fun testInlineVariablePositionsFromBackgroundThread() {
        val sourceCode = requireNotNull(javaClass.getResource("/inline_variables.sql")) {
            "Missing test resource: inline_variables.sql"
        }.readText().replace("$$", "\$function\$")
        val def = PlApiFunctionDef(0, "public", "inline_variables", sourceCode, sourceCode.md5())
        val plSource = PlFunctionSource(project, def, def.md5)

        val positions = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            mapOf(
                "argument" to plSource.sourcePositions("start_n", true, Int.MAX_VALUE).map { it.line },
                "variable" to plSource.sourcePositions("n", false, Int.MAX_VALUE).map { it.line },
                "missing" to plSource.sourcePositions("missing", false, Int.MAX_VALUE).map { it.line },
            )
        }).get()

        assertEquals(listOf(0, 10), positions["argument"])
        assertEquals(listOf(5, 8), positions["variable"])
        assertEmpty(positions["missing"].orEmpty())
    }
}

private fun String.md5(): String =
    MessageDigest.getInstance("MD5")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
