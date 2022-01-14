/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.debugger

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.PlatformIcons
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*

/**
 * During a debugging session the code definition does not change
 * The function definition have not to be reevaluated
 */
class XStack(private val session: XDebugSession) : XExecutionStack("") {

    private val frames = mutableListOf<XStackFrame>()
    private val psiRegistry = mutableMapOf<Long, Pair<Int, PsiFile?>>()
    private val plProcess: PlProcess by lazy {
        session.debugProcess as PlProcess
    }

    override fun getTopFrame(): XStackFrame? {
        return frames.firstOrNull()
    }

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        container?.addStackFrames(frames.subList(firstFrameIndex, frames.size), true)
    }

    fun update() {
        frames.clear()
        plProcess.getStack().forEach {
            val def = getFunction(it.oid)
            frames.add(XFrame(def?.second, (def?.first ?: 0) + it.line))
        }
    }

    private fun getFunction(oid: Long): Pair<Int, PsiFile?>? {

        if (!psiRegistry.containsKey(oid)) {
            val function = plGetFunctionDef(plProcess.connection, oid)
            var offset = 0
            function.source.split("\n").forEachIndexed { l, line ->
                if (line.lowercase().startsWith("as \$function\$")) offset = l - 1
            }
            val psi = runReadAction {
                PsiFileFactory.getInstance(session.project).createFileFromText(
                    "${function.name}::${function.oid}@${plProcess.sessionId}",
                    getPlLanguage(),
                    function.source
                )
            }
            psiRegistry[oid] = Pair(offset, psi)
        }
        return psiRegistry[oid]
    }


    inner class XFrame(private val psi: PsiFile?, private val pos: Int) : XStackFrame() {

        /**
         * Frame does not change if the line is the same
         */
        override fun getEqualityObject(): Any? {
            return "${psi?.name}@${pos}"
        }

        override fun getEvaluator(): XDebuggerEvaluator? {
            return super.getEvaluator()
        }

        override fun getSourcePosition(): XSourcePosition? {
            return XDebuggerUtil.getInstance().createPosition(psi?.virtualFile, pos)
        }

        override fun computeChildren(node: XCompositeNode) {
            val list = XValueChildrenList()
            val plVars = plGetStackVariables(plProcess.connection, plProcess.sessionId)
            val (args, values) = plVars.partition { it.isArg }
            if (args.isNotEmpty()) {
                list.addTopGroup(XValGroup("Arguments", args))
            }
            if (values.isNotEmpty()) {
                list.addBottomGroup(XValGroup("Values", values))
            }
            node.addChildren(list, true)
        }

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

        override fun hashCode(): Int {
            return plVar.hashCode()
        }

        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(
                PlatformIcons.FIELD_ICON,
                if (isArray()) "${plVar.arrayType}[]" else plVar.type,
                plVar.value,
                canExplode()
            )
        }

        override fun computeChildren(node: XCompositeNode) {
            val connection = plProcess.connection
            if (isArray()) {
                explode(node, plExplodeArray(connection, plVar))
            } else if (isComposite()) {
                //explode(node, plExplodeComposite(connection, session, plVar))
            }

        }

        override fun canNavigateToSource(): Boolean {
            return super.canNavigateToSource()
        }

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