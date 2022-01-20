/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import org.json.simple.parser.JSONParser

data class PlBoolean(val value: Boolean)

fun plBooleanProducer() = Producer<PlBoolean> {
    PlBoolean(it.bool())
}

data class PlInt(val value: Int)

fun plIntProducer() = Producer<PlInt> { PlInt(it.int()) }

data class PlLong(val value: Long)

fun plLongProducer() = Producer<PlLong> { PlLong(it.long()) }

data class PlString(val value: String)

public fun plStringProducer() = Producer<PlString> { PlString(it.string()) }

data class PlJson(val value: Any)

fun plJsonProducer() = Producer<PlJson> { PlJson(JSONParser().parse(it.string())) }

data class PlStep(val oid: Long, val line: Int, val target: String)

fun plStepProducer() = Producer<PlStep> { PlStep(it.long(), it.int(), it.string()) }

data class PlStackFrame(val level: Int, val target: String, val oid: Long, val line: Int, val args: String)

fun plStackProducer() = Producer<PlStackFrame> { PlStackFrame(it.int(), it.string(), it.long(), it.int(), it.string()) }

data class PlValue(
    val oid: Long,
    val name: String,
    val type: String,
    val kind: Char,
    val isArray: Boolean,
    val arrayType: String,
    val value: String
)

fun plValueProducer() = Producer<PlValue> {
    PlValue(it.long(), it.string(), it.string(), it.char(), it.bool(), it.string(), it.string())
}

data class PlStackVariable(
    val isArg: Boolean,
    val line: Int,
    val value: PlValue
)

fun plStackVariableProducer() = Producer<PlStackVariable> {
    PlStackVariable(
        it.bool(),
        it.int(),
        PlValue(it.long(), it.string(), it.string(), it.char(), it.bool(), it.string(), it.string())
    )
}

data class PlFunctionArg(val oid: Long, val pos: Int, val name: String, val type: String, val default: Boolean)

fun plFunctionArgProducer() = Producer<PlFunctionArg> {
    PlFunctionArg(
        it.long(),
        it.int(),
        it.string(),
        it.string(),
        it.bool(),
    )
}

data class PlFunctionDef(
    val oid: Long,
    val schema: String,
    val name: String,
    val source: String,
)

fun plVFunctionDefProducer() = Producer<PlFunctionDef> {
    PlFunctionDef(
        it.long(),
        it.string(),
        it.string(),
        it.string()
    )
}

data class PlStackBreakPoint(
    val func: Long,
    val line: Int
)

fun plStackBreakPointProducer() = Producer<PlStackBreakPoint> {
    PlStackBreakPoint(
        it.long(),
        it.int()
    )
}

data class PlExtension(val schema: String, val name: String, val version: String);

fun plExtensionProducer() = Producer<PlExtension> {
    PlExtension(it.string(), it.string(), it.string())
}