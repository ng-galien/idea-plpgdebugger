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
 * Represents the settings for the PL plugin.
 *
 * @property attachTimeOut The timeout value in milliseconds for attaching to a running process. Default value is 3000.
 * @property stepTimeOut The timeout value in milliseconds for each debugging step. Default value is 3000.
 * @property showInlineVariable Determines whether inline variable values should be shown during debugging. Default value is true.
 * @property enableCustomCommand Determines whether custom command functionality is enabled. Default value is false.
 * @property customCommand The custom command to be executed during debugging. Default value is "SELECT 'DEBUGGER PRE COMMAND';".
 * @property showNotice Determines whether notices should be shown during debugging. Default value is true.
 * @property showInfo Determines whether info messages should be shown during debugging. Default value is false.
 * @property showCmd Determines whether command execution details should be shown during debugging. Default value is false.
 * @property showDebug Determines whether debug messages should be shown during debugging. Default value is false.
 * @property showSQL Determines whether SQL statements should be shown during debugging. Default value is false.
 * @property failExtension Determines whether extension failure should be simulated during debugging. Default value is false.
 * @property failDetection Determines whether failure in triggering the debugging detection should be simulated during debugging. Default value is false.
 * @property failStart Determines whether failure in starting the debugging should be simulated during debugging. Default value is false.
 * @property failPGBreak Determines whether failure in breaking the debugging should be simulated during debugging. Default value is false.
 * @property failAttach Determines whether failure in attaching to the process should be simulated during debugging. Default value is false.
 * @property customQuery Determines whether custom queries should be enabled during debugging. Default value is false.
 * @property queryFuncArgs The SQL query to fetch function call arguments. Default value is ApiQuery.GET_FUNCTION_CALL_ARGS.sql.
 * @property queryRawVars The SQL query to fetch raw variables. Default value is ApiQuery.GET_RAW_VARIABLES.sql.
 * @property queryExplodeComposite The SQL query to explode composite variables. Default value is ApiQuery.EXPLODE_COMPOSITE.sql.
 * @property queryExplodeArray The SQL query to explode array variables. Default value is ApiQuery.EXPLODE_ARRAY.sql.
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
