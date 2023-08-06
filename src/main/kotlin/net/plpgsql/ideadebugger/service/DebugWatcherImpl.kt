/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.service

import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.util.SearchPath
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.DebugMode
import net.plpgsql.ideadebugger.getLogger
import java.sql.Connection

@Service
class DebugWatcherImpl: DebugWatcher {

    companion object {
        val LOG = getLogger(DebugWatcherImpl::class)
    }

//    private var currentProcess: PlProcess? = null
//    private var mode: DebugMode = DebugMode.NONE
//    private var oid: Long = EMPTY_ENTRY_POINT
//    private var session = INVALID_SESSION

    override fun getDebugMode(): DebugMode {
        TODO()
    }

    override fun getFunctionOid(): Long {
        TODO()
    }

    override fun getDbInternalSession(): Int {
        TODO()
    }

    override fun registerConnectionPoint(project: Project,
                                         connectionPoint: DatabaseConnectionPoint,
                                         searchPath: SearchPath?) {
        LOG.debug("Registering connection point: ${connectionPoint.name}")
        //Get the credentials from the credential store
        val credentials = DatabaseCredentials.getInstance().getCredentials(connectionPoint);
        LOG.debug("Credentials: ${credentials}")

    }

    override fun sqlConnection(): Connection? {
        TODO("Not yet implemented")
    }

    override fun isDebugging(): Boolean {
//        return mode != DebugMode.NONE
        return false
    }

//    override fun processStarted(process: PlProcess, debugMode: DebugMode, functionOid: Long) {
//        console("Watcher: started")
//        currentProcess = process
//        mode = debugMode
//        oid = functionOid
//        session = process.executor.getDbInternalSession()
//        publishEvent(ProcessCreated(process.project, ProcessItem(process, mode)))
//    }

//    override fun processFinished(process: PlProcess) {
//        console("Watcher: finished")
//        publishEvent(ProcessFinished(process.project, ProcessItem(process, mode)))
//        currentProcess = null
//        mode = DebugMode.NONE
//        oid = 0L
//    }
//
//    override fun getProcess(): PlProcess? {
//        return currentProcess
//    }

//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as DebugWatcherImpl
//
//        return session == other.session
//    }
//
//    override fun hashCode(): Int {
//        return session
//    }


}