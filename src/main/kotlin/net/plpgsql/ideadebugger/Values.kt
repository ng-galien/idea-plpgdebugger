package net.plpgsql.ideadebugger

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.intellij.icons.AllIcons
import javax.swing.Icon

fun iconForStackValue(stackValue: PostgresLib.StackValue?): Icon {
    return stackValue?.let {
        if (it.isArray) AllIcons.Debugger.Db_array
        else if (it.isComposite()) AllIcons.Nodes.Type
        else AllIcons.Nodes.Variable
    }?: AllIcons.General.Error
}

fun canExplodeStackValue(stackValue: PostgresLib.StackValue?): Boolean {
    return stackValue?.let {
        !it.isNull() && (it.isArray || it.isComposite() || isJsonNonNull(it))
    }?: false
}

private fun isJsonNonNull(stackValue: PostgresLib.StackValue): Boolean {
    if (!stackValue.isJSON()) {
        return false
    }
    return when(val jsonElement = JsonParser.parseString(stackValue.value)) {
        is JsonObject -> jsonElement.entrySet().isNotEmpty()
        is JsonArray -> !jsonElement.isEmpty
        else -> false
    }
}

fun childrenValues(parenValue: PostgresLib.StackValue): List<PostgresLib.StackValue> {

    return if (parenValue.isArray) {
        runInternal { it.stackArrayValues(PostgresLib.Oid(parenValue.oid), parenValue.value) }?: emptyList()
    } else if(parenValue.isComposite()) {
        runInternal { it.stackCompositeValues(PostgresLib.Oid(parenValue.oid), parenValue.value) }?: emptyList()
    } else if(parenValue.isJSON()) {
        childrenValuesForJson(parenValue)
    }
    else {
        emptyList()
    }
}

private fun childrenValuesForJson(parentValue: PostgresLib.StackValue): List<PostgresLib.StackValue> {
    return if (parentValue.isJSON()) {
        when(val jsonElement = JsonParser.parseString(parentValue.value)) {
            is JsonObject -> {
                jsonElement.entrySet().map {
                    jsonStackValue(it.key, parentValue, it.value)
                }
            }
            is JsonArray -> {
                listOf<PostgresLib.StackValue>().toMutableList().apply {
                    jsonElement.forEachIndexed { index, jsonElement ->
                        add(jsonStackValue("${parentValue.name}[$index]", parentValue, jsonElement))
                    }
                }
            }
            else -> listOf()
        }
    } else {
        emptyList()
    }
}

private fun jsonStackValue(name: String, parenValue: PostgresLib.StackValue, jsonElement: JsonElement) =
    PostgresLib.StackValue(
        oid = parenValue.oid,
        isArg = parenValue.isArg,
        line = parenValue.line,
        kind = if(jsonElement is JsonObject) "c" else parenValue.kind,
        value = jsonElement.toString(),
        pretty = jsonElement.toString(),
        name = name,
        type = jsonType(jsonElement),
        arrayType = "",
        isArray = jsonElement is JsonArray,
        isText = jsonElement is JsonPrimitive && jsonElement.isString,)

private fun jsonType(jsonElement: JsonElement): String {
    return when(jsonElement) {
        is JsonObject -> "json"
        is JsonArray -> "jsonArray"
        is JsonPrimitive -> {
            when {
                jsonElement.isString -> "text"
                jsonElement.isNumber -> "numeric"
                jsonElement.isBoolean -> "boolean"
                else -> "unknown"
            }
        }
        else -> "unknown"
    }
}
