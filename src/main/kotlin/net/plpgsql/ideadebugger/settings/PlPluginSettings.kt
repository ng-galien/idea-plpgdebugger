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

import net.plpgsql.ideadebugger.command.ApiQuery

/**
 * Settings for the PL/SQL Debugger
 *
 * @author Alexandre Boyer
 */
data class PlPluginSettings(
    var attachTimeOut: Int = 3000,
    var stepTimeOut: Int = 3000,
    var showInlineVariable: Boolean = true,
    var enableCustomCommand: Boolean = false,
    var customCommand: String = "SELECT 'DEBUGGER PRE COMMAND';",
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
