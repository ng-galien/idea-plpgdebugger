/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.settings

import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
data class PlPluginSettings(
    var attachTimeOut: Int = 3000,
    var stepTimeOut: Int = 3000,
    var preRunCommand: String = "",
    var preDebugCommand: String = "",
    var showCmd: Boolean = false,
    var showDebug: Boolean = false,
    var showInfo: Boolean = false,
    var showNotice: Boolean = true,
)