/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.plpgsql.ideadebugger

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
//import org.testcontainers.junit.jupiter.Container
//import org.testcontainers.junit.jupiter.Testcontainers

//@Testcontainers
class PostgresContainerTest {
//
//    @Container
//    var postgres = getPGContainer("15")
//
//    @BeforeEach
//    fun setup() {
//        postgres.start()
//        getHandle(postgres).use {
//            it.execute("CREATE EXTENSION if not exists pldbgapi;")
//        }
//    }
//
//    @TestFactory
//    fun `test retrieve function source`() = listOf(
//        "function_with_declare",
//        "function_with_declare_with_comments",
//        "function_without_declare",
//        "function_without_declare_with_comments",
//    ).map { name ->
//        DynamicTest.dynamicTest("the function $name should be loaded and retrieved" ) {
//            val sourceCode = getFunctionSource(this, postgres, name)
//            assertNotNull(sourceCode)
//
//        }
//    }
}
//