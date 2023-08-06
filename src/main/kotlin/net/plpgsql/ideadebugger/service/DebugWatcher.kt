/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.service

import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.util.SearchPath
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.DebugMode
import java.sql.Connection

interface DebugWatcher {
    fun registerConnectionPoint(project: Project, connectionPoint: DatabaseConnectionPoint, searchPath: SearchPath?)
    fun sqlConnection(): Connection?
    fun isDebugging(): Boolean
//    fun processStarted(process: PlProcess, debugMode: DebugMode, functionOid: Long)
//    fun processFinished(process: PlProcess)
//    fun getProcess(): PlProcess?
    fun getDebugMode(): DebugMode
    fun getFunctionOid(): Long

    fun getDbInternalSession(): Int
}