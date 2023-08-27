/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.breakpoint

import com.intellij.database.debugger.SqlLineBreakpointProperties

data class DBBreakpointProperties(val oid: Long, val dbLine: Int) : SqlLineBreakpointProperties()
