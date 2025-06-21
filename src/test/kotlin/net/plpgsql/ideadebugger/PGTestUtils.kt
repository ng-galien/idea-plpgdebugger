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

import com.google.common.hash.Hashing
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin

const val PG_PORT = 5432

data class FunctionDef(val schema: String, val routine: String, val args: Map<String,  String>)

fun String.md5(): String = Hashing.md5().hashString(this, Charsets.UTF_8).toString()

fun loadProcedure(obj: Any, handle: Handle, functionName: String) {
    val fileContent = obj.javaClass.getResourceAsStream("/${functionName}.sql")?.bufferedReader().use { it?.readText() }
    handle.execute(fileContent)
}

fun getProcedureDefinition(handle: Handle, procedureName: String): String? {
    return handle.createQuery("SELECT pg_get_functiondef(oid) FROM pg_catalog.pg_proc WHERE proname = :proname")
        .bind("proname", procedureName)
        .mapTo(String::class.java)
        .firstOrNull()
}


fun getHandle(): Handle {
    val host = "localhost"
    val port = 5515
    val jdbi = Jdbi.create("jdbc:postgresql://${host}:${port}/postgres", "postgres", "postgres")
        .installPlugin(PostgresPlugin()).installPlugin(KotlinPlugin())
    return jdbi.open()
}

fun getFunctionSource(obj: Any, functionName: String): String {
    return getHandle().use {
        loadProcedure(obj, it, functionName)
        getProcedureDefinition(it, functionName)?: throw Exception("Function $functionName not found")
    }
}
