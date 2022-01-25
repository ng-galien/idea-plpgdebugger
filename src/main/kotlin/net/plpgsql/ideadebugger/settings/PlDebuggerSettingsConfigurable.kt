/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class PlDebuggerSettingsConfigurable : Configurable {

    var panel: DialogPanel? = null
    val data = PlDebuggerSettingsState.getInstance().toSettings()

    override fun createComponent(): JComponent? {
        panel = plParametersPanel()
        return panel
    }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
        PlDebuggerSettingsState.getInstance().fromSettings(data)
    }

    override fun reset() {
        panel?.reset()
    }


    override fun getDisplayName(): String = "PL/pg Debugger"

    override fun disposeUIResources() {
        panel = null
    }

    private fun plParametersPanel(): DialogPanel {

        val res = panel {
            row("Attach timeout(ms)") {
                intTextField().bindIntText(data::attachTimeOut)
            }
            row("Step timeout(ms)") {
                intTextField().bindIntText(data::stepTimeOut)
            }
            /*
            row("Run pre-command") {
                textField().bindText(data::preRunCommand)
            }
            row("Debug pre-command") {
                textField().bindText(data::preDebugCommand)
            }*/
            row {
                checkBox("Show NOTICE")
                    .bindSelected(data::showNotice)
            }
            row {
                checkBox("Show info")
                    .bindSelected(data::showInfo)
            }
            row {
                checkBox("Show API")
                    .bindSelected(data::showCmd)
            }
            row {
                checkBox("Show debug")
                    .bindSelected(data::showDebug)
            }
        }
        return res
    }


}