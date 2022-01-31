/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.settings

import net.plpgsql.ideadebugger.ApiQuery
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
data class PlPluginSettings(
    var attachTimeOut: Int = 3000,
    var stepTimeOut: Int = 3000,
    var showInlineVariable: Boolean = true,
    var enableSessionCommand: Boolean = false,
    var enableDebuggerCommand: Boolean = false,
    var sessionCommand: String = "SELECT 'SESSION PRE COMMAND';",
    var debuggerCommand: String = "SELECT 'DEBUGGER PRE COMMAND';",
    var showNotice: Boolean = true,
    var showInfo: Boolean = false,
    var showCmd: Boolean = false,
    var showDebug: Boolean = false,
    var showSQL: Boolean = false,
    var failExtension: Boolean = false,
    var failDetection: Boolean = false,
    var failStart: Boolean = false,
    var failPGBreak: Boolean = false,
    var failAttach: Boolean = false,
    var customQuery: Boolean = false,
    var queryFuncArgs: String = ApiQuery.GET_FUNCTION_CALL_ARGS.sql,
    var queryRawVars: String = ApiQuery.GET_RAW_VARIABLES.sql,
    var queryExplodeComposite: String = ApiQuery.EXPLODE_COMPOSITE.sql,
    var queryExplodeArray: String = ApiQuery.EXPLODE_ARRAY.sql,
)