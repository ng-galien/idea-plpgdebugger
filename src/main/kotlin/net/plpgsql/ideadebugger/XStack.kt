/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import icons.DatabaseIcons
import javax.swing.Icon

/**
 * During a debugging session the code definition does not change
 * The function definition have not to be reevaluated
 */
class XStack(process: PlProcess) : XExecutionStack("") {

    private val frames = mutableListOf<XFrame>()
    private val variableRegistry = mutableMapOf<Long, List<PlApiStackVariable>>()

    val executor: PlExecutor = process.controller.executor
    val project = process.controller.project

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

    inner class XFrame(val plFrame: PlApiStackFrame, val file: PlFunctionSource) :
        XStackFrame() {

        private val oid: Long
            get() = plFrame.oid

        fun getSourceRatio(): Double {
            if (file.lineRangeCount == 0) {
                return 1.0
            }
            return (plFrame.line + file.start - file.codeRange.first).toDouble() / file.lineRangeCount
        }

        override fun getEvaluator(): XDebuggerEvaluator? {
            return super.getEvaluator()
        }

        override fun getSourcePosition(): XSourcePosition? {
            return XDebuggerUtil.getInstance().createPosition(file, plFrame.line + file.start)
        }

        override fun computeChildren(node: XCompositeNode) {
            val list = XValueChildrenList()
            list.addTopGroup(XValGroup(GroupType.STACK, getFrameInfo()))
            val plVars = getVariables()
            val (args, values) = plVars.partition { it.isArg }
            if (args.isNotEmpty()) {
                list.addTopGroup(XValGroup(GroupType.PARAMETER, args))
            }
            if (values.isNotEmpty()) {
                list.addBottomGroup(XValGroup(GroupType.VALUE, values))
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

        private fun getFrameInfo(): List<PlApiStackVariable> = listOf<PlApiStackVariable>(
            PlApiStackVariable(
                false,
                0,
                PlAiValue(
                    0,
                    "Function",
                    "text",
                    'b',
                    false,
                    "",
                    file.name
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlAiValue(
                    0,
                    "Oid",
                    "int8",
                    'b',
                    false,
                    "",
                    "${plFrame.oid}"
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlAiValue(
                    0,
                    "Level",
                    "int4",
                    'b',
                    false,
                    "",
                    "${plFrame.level}"
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlAiValue(
                    0,
                    "Level",
                    "int4",
                    'b',
                    false,
                    "",
                    "${plFrame.level}"
                )
            ),
            PlApiStackVariable(
                false,
                0,
                PlAiValue(
                    0,
                    "Position",
                    "int4",
                    'b',
                    false,
                    "",
                    "${plFrame.line}"
                )
            ),
        )
    }


    inner class XValGroup(private val type: GroupType, private val plVars: List<PlApiStackVariable>) :
        XValueGroup(type.title) {

        override fun computeChildren(node: XCompositeNode) {
            val list = XValueChildrenList()
            plVars.forEach {
                list.add(XVal(it.value))
            }
            node.addChildren(list, true)
        }

        override fun isAutoExpand(): Boolean = (type != GroupType.STACK)

        override fun isRestoreExpansion(): Boolean = true

        override fun getIcon(): Icon {
            return type.icon
        }
    }

    inner class XVal(private val plVar: PlAiValue) : XNamedValue(plVar.name) {


        override fun computePresentation(node: XValueNode, place: XValuePlace) {

            node.setPresentation(
                if (isArray()) AllIcons.Debugger.Db_array
                else if (isComposite()) AllIcons.Nodes.Type
                else AllIcons.Nodes.Variable,
                if (isArray()) "${plVar.arrayType}[]" else plVar.type,
                plVar.value,
                canExplode()
            )
        }

        override fun computeChildren(node: XCompositeNode) {
            explode(node, executor.explode(plVar))
            executor.displayInfo()
        }

        override fun computeSourcePosition(navigatable: XNavigatable) {
            super.computeSourcePosition(navigatable)
        }

        override fun canNavigateToSource(): Boolean = true

        private fun explode(node: XCompositeNode, values: List<PlAiValue>) {
            val list = XValueChildrenList()
            values.forEach {
                list.add(XVal(it))
            }
            if (list.size() > 0) {
                node.addChildren(list, true)
            }
        }

        private fun isArray() = plVar.isArray
        private fun isComposite() = plVar.kind == 'c'
        private fun canExplode() = !plNull(plVar.value) && (isArray() || isComposite())
    }

}