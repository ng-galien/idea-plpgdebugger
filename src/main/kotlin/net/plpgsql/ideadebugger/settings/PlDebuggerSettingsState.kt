/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "net.plpgsql.ideadebugger.settings.PlProjectSettingState",
    storages = [Storage("PlDebuggerPlugin.xml")]
)
class PlDebuggerSettingsState : PersistentStateComponent<PlDebuggerSettingsState> {

    var attachTimeOut: Int = 3000
    var stepTimeOut: Int = 3000
    var preRunCommand: String = ""
    var preDebugCommand: String = ""
    var showCmd: Boolean = false
    var showDebug: Boolean = false
    var showInfo: Boolean = false
    var showNotice: Boolean = true

    override fun getState(): PlDebuggerSettingsState? = this

    override fun loadState(state: PlDebuggerSettingsState) {
        XmlSerializerUtil.copyBean(state, this);
    }

    companion object {
        fun getInstance(): PlDebuggerSettingsState =
            ApplicationManager.getApplication().getService(PlDebuggerSettingsState::class.java)
    }

    fun fromSettings(data: PlPluginSettings) {
        attachTimeOut = data.attachTimeOut
        stepTimeOut = data.stepTimeOut
        preRunCommand = data.preRunCommand
        preDebugCommand = data.preDebugCommand
        showDebug = data.showDebug
        showInfo = data.showInfo
        showNotice = data.showNotice
        showCmd = data.showCmd
    }

    fun toSettings(): PlPluginSettings =
        PlPluginSettings(
            attachTimeOut = attachTimeOut,
            stepTimeOut = stepTimeOut,
            preRunCommand = preRunCommand,
            preDebugCommand = preDebugCommand,
            showCmd = showCmd,
            showDebug = showDebug,
            showInfo = showInfo,
            showNotice = showNotice,
        )
}