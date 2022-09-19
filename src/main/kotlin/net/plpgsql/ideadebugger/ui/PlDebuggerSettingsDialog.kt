/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState
import javax.swing.JComponent

class PlDebuggerSettingsDialog : Configurable {

    private var panel: DialogPanel? = null
    private val formData = PlDebuggerSettingsState.getInstance().data

    override fun createComponent(): JComponent? {
        panel = plParametersPanel()
        return panel
    }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
        PlDebuggerSettingsState.getInstance().data = formData
    }

    override fun reset() {
        panel?.reset()
    }

    override fun getDisplayName(): String = "PostgreSQL Debugger"

    override fun disposeUIResources() {
        panel = null
    }

    private fun plParametersPanel(): DialogPanel {

        val res = panel {
            group("General") {
                row("Attach timeout(ms)") {
                    intTextField().bindIntText(formData::attachTimeOut)
                        .comment("The debugger should outputs PLDBGBREAK up until this delay")
                }
                row("Step timeout(ms)") {
                    intTextField().bindIntText(formData::stepTimeOut)
                        .comment("Maximum time allowed for a step command")
                }
                row {
                    checkBox("Display variables in code")
                        .bindSelected(formData::showInlineVariable)
                }
            }
            collapsibleGroup("Custom Commands") {
                row {
                    checkBox("Enable custom command")
                        .bindSelected(formData::enableCustomCommand)
                }
                row("Custom command") {
                    textArea()
                        .bindText(formData::customCommand)
                        .resizableColumn()
                        .horizontalAlign(HorizontalAlign.FILL)
                        .comment(
                            """
                            Optional command run on the internal debugger session 
                            """.trimIndent())
                }
            }
            collapsibleGroup("Debugger Output") {
                row {
                    checkBox("Show NOTICE")
                        .bindSelected(formData::showNotice)
                }
                row {
                    checkBox("Show info")
                        .bindSelected(formData::showInfo)
                }
                row {
                    checkBox("Show API")
                        .bindSelected(formData::showCmd)
                }
                row {
                    checkBox("Show SQL")
                        .bindSelected(formData::showSQL)
                }
                row {
                    checkBox("Show debug")
                        .bindSelected(formData::showDebug)
                }
            }
            collapsibleGroup("Developer Settings") {

                collapsibleGroup("Queries") {

                    row {
                        checkBox("Use custom queries")
                            .bindSelected(formData::customQuery)
                            .comment(
                                """
                                Customize queries for solving compatibility issues 
                                """.trimIndent())
                    }

                    row("Function detection") {
                        textArea()
                            .bindText(formData::queryFuncArgs)
                            .resizableColumn()
                            .horizontalAlign(HorizontalAlign.FILL)
                            .comment(
                                """
                                Fetch arguments of the function<br/>
                                Schema and function name are passed as arguments 
                                """.trimIndent())
                    }
                    row("Fetch variables") {
                        textArea()
                            .bindText(formData::queryRawVars)
                            .resizableColumn()
                            .horizontalAlign(HorizontalAlign.FILL)
                            .comment(
                                """
                                Fetch variable definition and values<br/>
                                Debugging session is passed as single argument
                                """.trimIndent())
                    }
                    row("Explode composite") {

                        textArea()
                            .bindText(formData::queryExplodeComposite)
                            .resizableColumn()
                            .horizontalAlign(HorizontalAlign.FILL)
                            .comment(
                                """
                                Fetch members of a composite value in the stack<br/>
                                Json value is passed as single argument
                                """.trimIndent())
                    }
                    row("ExplodeArray") {

                        textArea()
                            .bindText(formData::queryExplodeArray)
                            .resizableColumn()
                            .horizontalAlign(HorizontalAlign.FILL)
                            .comment(
                                """
                                Fetch members of an array value in the stack<br/>
                                Json value is passed as single argument
                                """.trimIndent())
                    }
                }

                collapsibleGroup("Failure Testing") {
                    row {
                        checkBox("Extension failure")
                            .bindSelected(formData::failExtension)
                    }
                    row {
                        checkBox("Query detection failure")
                            .bindSelected(formData::failDetection)
                    }
                    row {
                        checkBox("Start debug failure")
                            .bindSelected(formData::failStart)
                    }
                    row {
                        checkBox("PLDBGBREAK failure")
                            .bindSelected(formData::failPGBreak)
                    }
                    row {
                        checkBox("Process attachment failure")
                            .bindSelected(formData::failAttach)
                    }
                }
            }
        }
        return res
    }
}