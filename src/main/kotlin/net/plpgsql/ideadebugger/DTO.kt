/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

data class PlBoolean(val value: Boolean)

data class PlInt(val value: Int)

data class PlLong(val value: Long)

data class PlString(val value: String)

data class PlStep(val oid: Long, val line: Int)

data class PlStackFrame(val level: Int, val target: String, val oid: Long, val line: Int, val args: String)

data class PlValue(
    val oid: Long,
    val name: String,
    val type: String,
    val kind: Char,
    val isArray: Boolean,
    val arrayType: String,
    val value: String
)

data class PlStackVariable(
    val isArg: Boolean,
    val line: Int,
    val value: PlValue
)

data class PlFunctionArg(
    val oid: Long,
    val nb: Int,
    val pos: Int,
    val name: String,
    val type: String,
    val default: Boolean
)

data class PlFunctionDef(
    val oid: Long,
    val schema: String,
    val name: String,
    val source: String,
    val hash: String
)

data class PlExtension(
    val schema: String,
    val name: String,
    val version: String
)
