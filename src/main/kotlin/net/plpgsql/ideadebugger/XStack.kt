/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.util.PlatformIcons
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import org.jetbrains.concurrency.runAsync

/**
 * During a debugging session the code definition does not change
 * The function definition have not to be reevaluated
 */
class XStack(private val session: XDebugSession) : XExecutionStack("") {

    private val frames = mutableListOf<XStackFrame>()
    private val fileRegistry = mutableListOf<PlFile>()
    private val variableRegistry = mutableMapOf<Long, List<PlStackVariable>>()
    val remoteBreakPoints = mutableListOf<PlStackBreakPoint>()

    val proc: PlProcess by lazy {
        session.debugProcess as PlProcess
    }
    private var currentStep: PlStep? = null

    override fun getTopFrame(): XStackFrame? {
        return frames.firstOrNull()
    }

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        container?.addStackFrames(frames.subList(firstFrameIndex, frames.size), true)
    }

    fun update(step: PlStep?) {
        currentStep = step

        frames.clear()
        val plStacks = plGetStack(proc)

        remoteBreakPoints.clear()
        remoteBreakPoints.addAll(plGetPlStackBreakPoint(proc))

        if (currentStep != null) {
            if (plStacks.find { it.oid == currentStep!!.oid && it.level == 0 } == null) {
                throw Exception("Invalid stack")
            }
        }
        plStacks.forEach {
            val file = getFunction(it.oid)

            file?.stackPos = it.line
            frames.add(XFrame(it, file))
        }
    }

    private fun getFunction(oid: Long): PlFile? {

        var file = fileRegistry.find { it.oid == oid }
        if (file == null) {
            file = PlFile(this, plGetFunctionDef(proc, oid), remoteBreakPoints)
            fileRegistry.add(file)
        }
        return file
    }


    inner class XFrame(private val frame: PlStackFrame, private val plFile: PlFile?) :
        XStackFrame() {

        override fun getEvaluator(): XDebuggerEvaluator? {
            return super.getEvaluator()
        }

        override fun getSourcePosition(): XSourcePosition? {
            return plFile?.getPosition()
        }

        override fun computeChildren(node: XCompositeNode) {
            val list = XValueChildrenList()
            list.addTopGroup(XValGroup("Stack", getFrameInfo()))
            val plVars = getVariables().blockingGet(2000)
            if (plVars != null) {
                val (args, values) = plVars.partition { it.isArg }
                if (args.isNotEmpty()) {
                    list.addTopGroup(XValGroup("Arguments", args))
                }
                if (values.isNotEmpty()) {
                    list.addBottomGroup(XValGroup("Values", values))
                }
            }
            node.addChildren(list, true)
        }

        private fun getVariables() = runAsync {
            if (currentStep == null || currentStep?.oid == frame.oid) {
                val plVars = plGetStackVariables(proc)
                variableRegistry[frame.oid] = plVars
            }
            variableRegistry[frame.oid]
        }

        private fun getFrameInfo(): List<PlStackVariable> = listOf<PlStackVariable>(
            PlStackVariable(
                false,
                0,
                PlValue(0,
                    "Function",
                    "text",
                    'b',
                    false,
                    "",
                    frame.target)
            ),
            PlStackVariable(
                false,
                0,
                PlValue(0,
                    "OID",
                    "int8",
                    'b',
                    false,
                    "",
                    "${frame.oid}")
            ),
            PlStackVariable(
                false,
                0,
                PlValue(0,
                    "Level",
                    "int4",
                    'b',
                    false,
                    "",
                    "${frame.level}")
            ),
            PlStackVariable(
                false,
                0,
                PlValue(0,
                    "Line",
                    "int4",
                    'b',
                    false,
                    "",
                    "${frame.line}")
            ),
        )
    }

    inner class XValGroup(name: String, var plVars: List<PlStackVariable>) : XValueGroup(name) {
        override fun computeChildren(node: XCompositeNode) {
            val list = XValueChildrenList()
            plVars.forEach {
                list.add(XVal(it.value))
            }
            node.addChildren(list, true)
        }

        override fun isAutoExpand(): Boolean = true

        override fun isRestoreExpansion(): Boolean = true
    }

    inner class XVal(private val plVar: PlValue) : XNamedValue(plVar.name) {

        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(
                PlatformIcons.FIELD_ICON,
                if (isArray()) "${plVar.arrayType}[]" else plVar.type,
                plVar.value,
                canExplode()
            )
        }

        override fun computeChildren(node: XCompositeNode) {
            explode(node, plExplodeArray(proc, plVar))
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

    /**
     *
     */
    fun addBreakPoint(path: String, line: Int) {
        val file: PlFile? = fileRegistry.find {
            it.name == path
        }
        if (file?.breakPoints?.contains(file.breakPointPosFromFile(line)) == false) {
            file.breakPoints.add(file.breakPointPosFromFile(line))
            file.updateBreakPoint()
        }
    }

    /**
     *
     */
    fun deleteBreakPoint(path: String, line: Int) {
        var file = fileRegistry.find {
            it.name == path
        }
        if (file?.breakPoints?.remove(file.breakPointPosFromFile(line)) == true) {
            file?.updateBreakPoint()
        }
    }
}