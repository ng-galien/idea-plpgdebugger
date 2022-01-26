/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import net.plpgsql.ideadebugger.ApiQuery

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
    var failExtension: Boolean = false
    var failDetection: Boolean = false
    var failStart: Boolean = false
    var failPGBreak: Boolean = false
    var failAttach: Boolean = false
    var customQuery: Boolean = false
    var queryFuncArgs: String = ApiQuery.GET_FUNCTION_CALL_ARGS.sql
    var queryRawVars: String = ApiQuery.GET_RAW_VARIABLES.sql
    var queryExplodeComposite: String = ApiQuery.EXPLODE_COMPOSITE.sql
    var queryExplodeArray: String = ApiQuery.EXPLODE_ARRAY.sql

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
        failExtension = data.failExtension
        failDetection = data.failDetection
        failStart = data.failStart
        failPGBreak = data.failPGBreak
        failAttach = data.failAttach
        customQuery = data.customQuery
        queryFuncArgs = data.queryFuncArgs
        queryRawVars = data.queryRawVars
        queryExplodeComposite = data.queryExplodeComposite
        queryExplodeArray = data.queryExplodeArray
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
            failExtension = failExtension,
            failDetection = failDetection,
            failStart = failStart,
            failPGBreak = failPGBreak,
            failAttach = failAttach,
            customQuery = customQuery,
            queryFuncArgs = queryFuncArgs,
            queryRawVars = queryRawVars,
            queryExplodeComposite = queryExplodeComposite,
            queryExplodeArray = queryExplodeArray,
        )
}