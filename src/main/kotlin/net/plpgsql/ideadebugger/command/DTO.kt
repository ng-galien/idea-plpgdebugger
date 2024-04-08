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

package net.plpgsql.ideadebugger.command

/**
 *
 */
data class PlApiVoid(
    val void: Int = 0
)

/**
 *
 */
data class PlApiBoolean(
    val value: Boolean
)

/**
 *
 */
data class PlApiInt(
    val value: Int
)

/**
 *
 */
data class PlApiString(
    val value: String
)

/**
 *
 */
data class PlActivity(
    val pid: Long,
    val application: String,
    val user: String,
    val address: String
)

/**
 *
 */
data class PlApiStep(
    val oid: Long,
    val line: Int,
    val md5: String
)

/**
 *
 */
data class PlApiStackFrame(
    val level: Int,
    val oid: Long,
    val line: Int,
    val md5: String
)

/**
 *
 */
data class PlApiValue(
    val oid: Long,
    val name: String,
    val type: String,
    val kind: Char,
    val isArray: Boolean,
    val isText: Boolean,
    val arrayType: String,
    val value: String,
    val pretty: String
)

/**
 *
 */
data class PlApiStackVariable(
    val isArg: Boolean,
    val line: Int,
    val value: PlApiValue,
)

/**
 *
 */
data class PlApiFunctionArg(
    val oid: Long,
    val nb: Int,
    val pos: Int,
    val name: String,
    val type: String,
    val default: Boolean,
)

/**
 *
 */
data class PlApiFunctionDef(
    val oid: Long,
    val schema: String,
    val name: String,
    val source: String,
    val md5: String,
)

/**
 *
 */
data class PlApiExtension(
    val schema: String,
    val name: String,
    val version: String,
)

