/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import icons.DatabaseIcons
import org.jetbrains.concurrency.runAsync
import javax.swing.Icon

/**
 * During a debugging session the code definition does not change
 * The function definition have not to be reevaluated
 */
class XStack(private val session: XDebugSession) : XExecutionStack("") {

    private val frames = mutableListOf<XFrame>()
    private val variableRegistry = mutableMapOf<Long, List<PlStackVariable>>()

    val controller: PlController by lazy {
        (session.debugProcess as PlProcess).controller
    }
    lateinit var currentStep: PlStep



    override fun getTopFrame(): XStackFrame? {
        return frames.firstOrNull()
    }

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        container?.addStackFrames(frames.subList(firstFrameIndex, frames.size), true)
    }

    fun updateRemote(step: PlStep): PlFile {

        currentStep = step
        frames.clear()
        val plStacks = controller.executor.getStack()
        if (currentStep.oid > 0) {
            if (plStacks.find { it.oid == currentStep.oid && it.level == 0 } == null) {
                throw Exception("Invalid stack")
            }
        }
        plStacks.forEach {
            frames.add(XFrame(it, getRemoteFunction(it.oid, it.line)))
        }
        assert(frames.isNotEmpty())
        return frames.first().plFile
    }

    private fun getRemoteFunction(oid: Long, pos: Int): PlFile {
        val file = PlVFS.getInstance().findFileByPath("$oid")
        return if (file is PlFile) {
            file
        } else {
            val funDef = controller.executor.getFunctionDef(oid)
            PlVFS.getInstance().register(PlFile(funDef, this))
        }.updateStack(this, pos)
    }

    enum class GroupType(val title: String, val icon: Icon) {
        STACK("Stack", AllIcons.Nodes.Method),
        PARAMETER("Parameters", AllIcons.Nodes.Parameter),
        VALUE("Values", DatabaseIcons.Table)
    }

    inner class XFrame(private val frame: PlStackFrame, val plFile: PlFile) :
        XStackFrame() {

        override fun getEvaluator(): XDebuggerEvaluator? {
            return super.getEvaluator()
        }

        override fun getSourcePosition(): XSourcePosition? {
            return plFile?.getPosition()
        }

        override fun computeChildren(node: XCompositeNode) {
            val list = XValueChildrenList()
            list.addTopGroup(XValGroup(GroupType.STACK, getFrameInfo()))
            val plVars = getVariables().blockingGet(2000)
            if (plVars != null) {
                val (args, values) = plVars.partition { it.isArg }
                if (args.isNotEmpty()) {
                    list.addTopGroup(XValGroup(GroupType.PARAMETER, args))
                }
                if (values.isNotEmpty()) {
                    list.addBottomGroup(XValGroup(GroupType.VALUE, values))
                }
            }
            node.addChildren(list, true)
        }

        private fun getVariables() = runAsync {
            if (currentStep.oid == frame.oid) {
                val plVars = controller.executor.getVariables()
                variableRegistry[frame.oid] = plVars
            }
            variableRegistry[frame.oid]
        }

        private fun getFrameInfo(): List<PlStackVariable> = listOf<PlStackVariable>(
            PlStackVariable(
                false,
                0,
                PlValue(
                    0,
                    "Function",
                    "text",
                    'b',
                    false,
                    "",
                    frame.target
                )
            ),
            PlStackVariable(
                false,
                0,
                PlValue(
                    0,
                    "Oid",
                    "int8",
                    'b',
                    false,
                    "",
                    "${frame.oid}"
                )
            ),
            PlStackVariable(
                false,
                0,
                PlValue(
                    0,
                    "Level",
                    "int4",
                    'b',
                    false,
                    "",
                    "${frame.level}"
                )
            ),
            PlStackVariable(
                false,
                0,
                PlValue(
                    0,
                    "First Position",
                    "int4",
                    'b',
                    false,
                    "",
                    "${plFile.firstPos}"
                )
            ),
            PlStackVariable(
                false,
                0,
                PlValue(
                    0,
                    "Stack Position",
                    "int4",
                    'b',
                    false,
                    "",
                    "${plFile.stackPos}"
                )
            ),
        )
    }


    inner class XValGroup(private val type: GroupType, private val plVars: List<PlStackVariable>) :
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

        override fun getIcon(): Icon? {
            return type.icon
        }
    }

    inner class XVal(private val plVar: PlValue) : XNamedValue(plVar.name) {


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
            explode(node, controller.executor.explode(plVar))
        }

        override fun computeSourcePosition(navigatable: XNavigatable) {
            super.computeSourcePosition(navigatable)
        }

        override fun canNavigateToSource(): Boolean = true

        private fun explode(node: XCompositeNode, values: List<PlValue>) {
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