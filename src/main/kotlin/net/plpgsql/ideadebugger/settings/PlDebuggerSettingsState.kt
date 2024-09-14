/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.plpgsql.ideadebugger.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Represents the state of the PL debugger settings.
 *
 * This class is used to store and retrieve persistent state information for the PL debugger plugin.
 * It implements the `PersistentStateComponent` interface with the `PlPluginSettings` class as the state type.
 * The state data is stored in an XML file named "PlDebuggerPlugin.xml".
 *
 * @property data The PL plugin settings data.
 */
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
