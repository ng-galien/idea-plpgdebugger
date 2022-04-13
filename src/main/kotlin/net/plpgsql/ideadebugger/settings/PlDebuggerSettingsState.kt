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
class PlDebuggerSettingsState : PersistentStateComponent<PlPluginSettings> {

    var data = PlPluginSettings()

    override fun getState(): PlPluginSettings = data

    companion object {
        fun getInstance(): PlDebuggerSettingsState =
            ApplicationManager.getApplication().getService(PlDebuggerSettingsState::class.java)
    }

    override fun loadState(state: PlPluginSettings) {
        XmlSerializerUtil.copyBean(state, data)
    }

}