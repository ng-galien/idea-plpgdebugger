package net.plpgsql.ideadebugger

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import icons.DatabaseIcons
import javax.swing.Icon
import kotlin.math.min

const val STACK_NAME = "PL/pgSQL"

private enum class StackGroup(val title: String, val icon: Icon) {
    STACK("Stack", AllIcons.Nodes.Method),
    PARAMETER("Parameters", AllIcons.Nodes.Parameter),
    VALUE("Values", DatabaseIcons.Table)
}

class PluginStack(val session: PostgresLib.DebugSession) : XExecutionStack(STACK_NAME, DatabaseIcons.Table) {
    private val pluginFrames = mutableListOf<PluginFrame>()
    override fun getTopFrame(): XStackFrame? = pluginFrames.firstOrNull()
    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        container?.addStackFrames(pluginFrames.subList(firstFrameIndex, pluginFrames.size), true)
    }

    fun updateFrames(frames: List<PostgresLib.Frame>) {
        pluginFrames.clear()
        pluginFrames.addAll(frames.map { PluginFrame(it, this) })
    }
}

class PluginFrame(private val frame: PostgresLib.Frame, private val stack: PluginStack) : XStackFrame() {

    val pluginFile: PluginFile? by lazy {
        findFileByFrame(frame)
    }

    val stackValues: List<PostgresLib.StackValue> by lazy {
        runInternal { it.stackValues(stack.session) }?: emptyList()
    }

    override fun getSourcePosition(): XSourcePosition? =
        pluginFile?.let {
            XDebuggerUtil.getInstance().createPosition(it, postgresLineToFileLine(frame.lineNumber, it))
        }

    override fun computeChildren(node: XCompositeNode) {
        val list = XValueChildrenList()
//        list.addTopGroup(PluginValueGroup(DBStack.GroupType.STACK, getFrameInfo()))
        val variables = pluginFile?.variables ?: emptyList()
        val (params, values) = variables.partition { it.isParam }
        if (params.isNotEmpty()) {
            list.addTopGroup(PluginValueGroup(StackGroup.PARAMETER, params, this))
        }
        if (values.isNotEmpty()) {
            list.addBottomGroup(PluginValueGroup(StackGroup.VALUE, values, this))
        }
        node.addChildren(list, true)
    }

}

private class PluginValueGroup(
    private val group: StackGroup,
    private val variables: List<FileVariable>,
    private val frame: PluginFrame
) : XValueGroup(group.title) {
    override fun computeChildren(node: XCompositeNode) {
        val list = XValueChildrenList()
        variables.forEach {
            list.add(PluginValue(frame, FileValueProvider(frame, it)))
        }
        node.addChildren(list, true)
    }

    override fun isAutoExpand(): Boolean = (group != StackGroup.STACK)

    override fun isRestoreExpansion(): Boolean = true

    override fun getIcon(): Icon {
        return group.icon
    }
}

interface PluginValueProvider {
    val name: String
    val stackValue: PostgresLib.StackValue?
}

class FileValueProvider(var frame: PluginFrame, fileVar: FileVariable) : PluginValueProvider {
    override val name: String = fileVar.name
    override val stackValue: PostgresLib.StackValue? by lazy {
        frame.stackValues.find { it.name == fileVar.name }
    }
}

class StackValueProvider(val stack: PostgresLib.StackValue) : PluginValueProvider {
    override val name: String = stack.name
    override val stackValue: PostgresLib.StackValue = stack
}

private class PluginValue (val frame: PluginFrame, val provider: PluginValueProvider) : XNamedValue(provider.name) {

    private val stackValue = provider.stackValue
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        node.setPresentation(
            iconForStackValue(stackValue),
            //if (isArray()) "${plVar.arrayType}[]" else plVar.type,
            PluginValuePresentation(stackValue),
            canExplodeStackValue(stackValue),
        )
        stackValue?.run {
            if (value.length > 50) {
                node.setFullValueEvaluator(object: XFullValueEvaluator(value.length) {
                    override fun startEvaluation(callback: XFullValueEvaluationCallback) {
                        callback.evaluated(value)
                    }

                })
            }
        }
    }

    override fun computeChildren(node: XCompositeNode) {
        stackValue?.run {
            val childrenValues = childrenValues(this)
            if (childrenValues.isNotEmpty()) {
                val list = XValueChildrenList()
                childrenValues.forEach {
                    list.add(PluginValue(frame, StackValueProvider(it)))
                }
                node.addChildren(list, true)
            }
        }
    }

    override fun computeSourcePosition(navigatable: XNavigatable) {
        psiElement()?.let {
            val pos = XDebuggerUtil.getInstance().createPositionByElement(it.parent)
            navigatable.setSourcePosition(pos)
        }
    }

    override fun computeInlineDebuggerData(callback: XInlineDebuggerDataCallback): ThreeState {
        if (!getSettings().showInlineVariable) {
            return ThreeState.NO
        }
        return psiReferences().map {
            val pos = XDebuggerUtil.getInstance().createPositionByElement(it)
            callback.computed(pos)
            ThreeState.YES
        }.firstOrNull() ?: ThreeState.NO
    }

    fun psiElement(): PsiElement? {
        return if (provider is FileValueProvider) {
            provider.frame.pluginFile?.let { file ->
                file.variables.find { it.name == provider.name }?.psiElement
            }
        } else null
    }


    private fun psiReferences(): List<PsiElement> {
        return psiElement()?.let {
            ReferencesSearch.search(it.parent).map { it.element }.toList()
        } ?: emptyList()
    }

    override fun getEvaluationExpression(): String {
        return ""
    }


    override fun canNavigateToSource(): Boolean = stackValue?.let { true } ?: false


}

class PluginValuePresentation(val stackValue: PostgresLib.StackValue?): XValuePresentation() {
    override fun renderValue(renderer: XValueTextRenderer) {
        stackValue?.run {
            val type = if (stackValue.isArray) "${this.arrayType}[]" else this.type
            renderer.renderComment("$type => ")
            val renderedValue = this.value
            renderer.renderValue(renderedValue.substring(0, min(renderedValue.length, 50)))
        }?:run {
            renderer.renderValue("Value is not available")
        }
    }
}

fun postgresLineToFileLine(line: Int, file: PluginFile): Int {
    val codeRange = file.codeRange
    return if (codeRange.first == 0) line else line - codeRange.first + 1
}

fun fileLineToPostgresLine(line: Int, file: PluginFile): Int {
    val codeRange = file.codeRange
    return if (codeRange.first == 0) line else line + codeRange.first - 1
}