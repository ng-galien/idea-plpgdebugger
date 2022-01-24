/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

data class PlApiBoolean(val value: Boolean)

data class PlApiInt(val value: Int)

data class PlApiLong(val value: Long)

data class PlApiString(val value: String)

data class PlApiStep(val oid: Long, val line: Int, val md5: String)

data class PlApiStackFrame(val level: Int, val oid: Long, val line: Int)

data class PlAiValue(
    val oid: Long,
    val name: String,
    val type: String,
    val kind: Char,
    val isArray: Boolean,
    val arrayType: String,
    val value: String,
)

data class PlApiStackVariable(
    val isArg: Boolean,
    val line: Int,
    val value: PlAiValue,
)

data class PlApiFunctionArg(
    val oid: Long,
    val nb: Int,
    val pos: Int,
    val name: String,
    val type: String,
    val default: Boolean,
)

data class PlApiFunctionDef(
    val oid: Long,
    val schema: String,
    val name: String,
    val source: String,
    val md5: String,
)

data class PlApiExtension(
    val schema: String,
    val name: String,
    val version: String,
)
