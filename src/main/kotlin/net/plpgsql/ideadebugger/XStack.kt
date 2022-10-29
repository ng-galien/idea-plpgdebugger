/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.google.gson.*
import com.intellij.icons.AllIcons
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import icons.DatabaseIcons
import net.plpgsql.ideadebugger.command.PlApiStackFrame
import net.plpgsql.ideadebugger.command.PlApiStackVariable
import net.plpgsql.ideadebugger.command.PlApiValue
import net.plpgsql.ideadebugger.command.PlExecutor
import net.plpgsql.ideadebugger.run.PlProcess
import net.plpgsql.ideadebugger.vfs.PlFunctionSource
import javax.swing.Icon
import kotlin.math.min

/**
 * During a debugging session the code definition does not change
 * The function definition have not to be reevaluated
 */
class XStack(process: PlProcess) : XExecutionStack("") {

    private val frames = mutableListOf<XFrame>()
    private val variableRegistry = mutableMapOf<Long, List<PlApiStackVariable>>()

    val executor: PlExecutor = process.executor
    val project = process.session.project

    override fun getTopFrame(): XStackFrame? {
        return frames.firstOrNull()
    }

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        container?.addStackFrames(frames.subList(firstFrameIndex, frames.size), true)
    }

    fun clear() {
        frames.clear()
    }

    fun append(frame: PlApiStackFrame, file: PlFunctionSource) {
        frames.add(XFrame(frame, file))
    }

    enum class GroupType(val title: String, val icon: Icon) {
        STACK("Stack", AllIcons.Nodes.Method),
        PARAMETER("Parameters", AllIcons.Nodes.Parameter),
        VALUE("Values", DatabaseIcons.Table)
    }

    /**
     * Debugger frame, per function call in the execution stack
     */
    inner class XFrame(val plFrame: PlApiStackFrame, val file: PlFunctionSource) : XStackFrame() {

        private val oid: Long
            get() = plFrame.oid

        fun getSourceRatio(): Double {
            if (file.lineRangeCount == 0) {
                return 1.0
            }
            return (plFrame.line + file.start - file.codeRange.first).toDouble() / file.lineRangeCount
        }

        override fun getSourcePosition(): XSourcePosition? {
            return XDebuggerUtil.getInstance().createPosition(file, getSourceLine())
        }

        fun getSourceLine(): Int = file.positionToLine(plFrame.line)

        override fun computeChildren(node: XCompositeNode) {
            val list = XValueChildrenList()
            list.addTopGroup(XValGroup(GroupType.STACK, getFrameInfo()))
            val plVars = getVariables()
            val (args, values) = plVars.partition { it.isArg }
            if (args.isNotEmpty()) {
                list.addTopGroup(XValGroup(GroupType.PARAMETER, args, this))
            }
            if (values.isNotEmpty()) {
                list.addBottomGroup(XValGroup(GroupType.VALUE, values, this))
            }
            node.addChildren(list, true)
        }

        private fun getVariables(): List<PlApiStackVariable> {
            if ((topFrame as XFrame?)?.oid == plFrame.oid) {
                val plVars = executor.getVariables()
                variableRegistry[plFrame.oid] = plVars
            }
            return variableRegistry[plFrame.oid] ?: mutableListOf()
        }

        private fun getFrameInfo(): List<PlApiStackVariable> = listOf(
            PlApiStackVariable(
                false,
                0,
                PlApiValue(
                    0,
                    "Function",
                    "text",
                    'b',
                    false,
                    "",
                    file.name,
                    file.name,
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlApiValue(
                    0,
                    "Oid",
                    "int8",
                    'b',
                    false,
                    "",
                    "${plFrame.oid}",
                    "${plFrame.oid}",
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlApiValue(
                    0,
                    "Level",
                    "int4",
                    'b',
                    false,
                    "",
                    "${plFrame.level}",
                    "${plFrame.level}",
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlApiValue(
                    0,
                    "Line",
                    "int4",
                    'b',
                    false,
                    "",
                    "${getSourceLine()}",
                    "${getSourceLine()}",
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlApiValue(
                    0,
                    "Range Start",
                    "int4",
                    'b',
                    false,
                    "",
                    "${file.codeRange.first}",
                    "${file.codeRange.first}",
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlApiValue(
                    0,
                    "Range End",
                    "int4",
                    'b',
                    false,
                    "",
                    "${file.codeRange.second}",
                    "${file.codeRange.second}",
                )
            ),
        )
    }

    /**
     * Group of stack values
     */
    inner class XValGroup(
        private val type: GroupType,
        private val plVars: List<PlApiStackVariable>,
        private val xFrame: XFrame? = null
    ) : XValueGroup(type.title) {
        override fun computeChildren(node: XCompositeNode) {
            val list = XValueChildrenList()
            plVars.forEach {
                list.add(XVal(it, xFrame))
            }
            node.addChildren(list, true)
        }

        override fun isAutoExpand(): Boolean = (type != GroupType.STACK)

        override fun isRestoreExpansion(): Boolean = true

        override fun getIcon(): Icon {
            return type.icon
        }
    }

    /**
     * Named Value from stack var
     */
    inner class XVal (
        private val plStackVar: PlApiStackVariable,
        private val xFrame: XFrame? = null
    ) : XNamedValue(plStackVar.value.name) {

        private val plVar: PlApiValue = plStackVar.value

        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(
                if (isArray()) AllIcons.Debugger.Db_array
                else if (isComposite()) AllIcons.Nodes.Type
                else AllIcons.Nodes.Variable,
                //if (isArray()) "${plVar.arrayType}[]" else plVar.type,
                object: XValuePresentation() {
                    override fun renderValue(renderer: XValueTextRenderer) {
                        val type = if (isArray()) "${plVar.arrayType}[]" else plVar.type
                        renderer.renderComment("$type => ")
                        val renderedValue = plVar.value
                        renderer.renderValue(renderedValue.substring(0, min(renderedValue.length, 50)))
                    }
                },
                canExplode()
            )
            if(plVar.value.length > 50) {
                node.setFullValueEvaluator(object: XFullValueEvaluator(plVar.value.length) {
                    override fun startEvaluation(callback: XFullValueEvaluationCallback) {
                        callback.evaluated(plVar.pretty)
                    }

                })
            }
        }

        override fun computeChildren(node: XCompositeNode) {
            val list = if (isJSON()) {
                when(val jsonElement = JsonParser.parseString(plVar.value)) {
                    is JsonObject -> {
                        jsonElement.entrySet().map {
                            fromJson(plVar.type, it.key, it.value)
                        }
                    }
                    is JsonArray -> {
                        listOf<PlApiValue>().toMutableList().apply {
                            jsonElement.forEachIndexed { index, jsonElement ->
                                add(fromJson(plVar.type, "${plVar.name}[$index]", jsonElement))
                            }
                        }
                    }
                    else -> listOf()
                }
            } else {
                executor.explode(plVar)
            }
            explode(node, list)
            executor.printStack()
        }


        override fun computeSourcePosition(navigatable: XNavigatable) {
            xFrame?.file?.let { fs ->
                (if (plStackVar.isArg) fs.psiArgs else fs.psiVariables).let { map ->
                    map[plVar.name]?.let {
                        val pos = XDebuggerUtil.getInstance().createPositionByElement(it)
                        navigatable.setSourcePosition(pos)
                    }
                }
                fs.psiUse[plVar.name]?.filter { p ->
                    xFrame.getSourceLine() >= p.first
                }?.forEach { p ->
                    val pos = XDebuggerUtil.getInstance().createPositionByElement(p.second)
                    navigatable.setSourcePosition(pos)
                }
            }
        }

        private fun fromJson(type: String, name: String, jsonElement: JsonElement): PlApiValue {
            val displayType = when(jsonElement) {
                is JsonPrimitive -> {
                    when {
                        jsonElement.isString -> "string"
                        jsonElement.isNumber -> "number"
                        jsonElement.isBoolean -> "boolean"
                        else -> "unknown"
                    }
                }
                else -> type
            }
            return when(jsonElement) {

                is JsonObject -> {
                    PlApiValue(0, name, displayType, 'b', false, "", jsonElement.toString(), jsonElement.toString())
                }
                is JsonArray -> {
                    PlApiValue(0, name, displayType, 'b', true, "json", jsonElement.toString(), jsonElement.toString())
                }
                is JsonPrimitive -> {
                    PlApiValue(0, name, displayType, 'b', false, "", jsonElement.toString(), jsonElement.toString())
                }
                else -> {
                    PlApiValue(0, name, displayType, 'b', false, "", jsonElement.toString(), jsonElement.toString())
                }
            }
        }

        override fun computeInlineDebuggerData(callback: XInlineDebuggerDataCallback): ThreeState {

            if (!getSettings().showInlineVariable) {
                return ThreeState.NO
            }
            var compute = false

            xFrame?.file?.let { fs ->
                (if (plStackVar.isArg) fs.psiArgs else fs.psiVariables).let { map ->
                    map[plVar.name]?.let {
                        val pos = XDebuggerUtil.getInstance().createPositionByElement(it)
                        callback.computed(pos)
                        compute = true
                    }
                }
                fs.psiUse[plVar.name]?.filter { p ->
                    xFrame.getSourceLine() >= p.first
                }?.forEach { p ->
                    val pos = XDebuggerUtil.getInstance().createPositionByElement(p.second)
                    callback.computed(pos)
                    compute = true
                }
            }
            return if (compute) ThreeState.YES else ThreeState.NO
        }

        override fun getEvaluationExpression(): String {
            return plVar.value
        }


        override fun canNavigateToSource(): Boolean = plStackVar.line > 0 || plStackVar.isArg


        private fun explode(node: XCompositeNode, values: List<PlApiValue>) {
            val list = XValueChildrenList()
            values.forEach {
                list.add(XVal(PlApiStackVariable(plStackVar.isArg, 0, it)))
            }
            node.addChildren(list, true)
        }

        private fun isArray() = plVar.isArray

        private fun isComposite() = plVar.kind == 'c'

        private fun isJSON() = plVar.type in listOf("json", "jsonb", "record", "jsonArray")

        private fun canExplode(): Boolean {
            if (plNull(plVar.value)) {
                return false
            }
            if (isArray()) {
                return plVar.value != "[]"
            }
            if (isJSON()) {
                return when(val jsonElement = JsonParser.parseString(plVar.value)) {
                    is JsonObject -> jsonElement.entrySet().isNotEmpty()
                    is JsonArray -> !jsonElement.isEmpty
                    else -> false
                }
            }
            return isComposite() || isJSON()
        }
    }

}