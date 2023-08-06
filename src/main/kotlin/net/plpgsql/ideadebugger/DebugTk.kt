/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.DGDepartment
import com.intellij.database.util.ErrorHandler
import com.intellij.database.util.GuardedRef
import com.intellij.database.util.SearchPath
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlStatement
import com.intellij.xdebugger.XDebuggerUtil
import net.plpgsql.ideadebugger.settings.PlDebuggerSettingsState
import net.plpgsql.ideadebugger.settings.PlPluginSettings
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin

const val CONSOLE = false
const val SELECT_NULL = "SELECT NULL;"
const val DEFAULT_SCHEMA = "public"
const val DBG_EXTENSION = "pldbgapi"
const val DBG_SHARED_LIBRARY = "plugin_debugger"
const val DBG_APP_NAME = "idea_debugger"
const val DBG_INTERNAL_APP_NAME = "idea_debugger_internal"
const val DBG_USER_APP_NAME = "idea_debugger_user"

//class DebugTk {
//    companion object {
//        val BUS = ApplicationManager.getApplication().messageBus
//        val VFS = PlVirtualFileSystem.Util.getInstance()
//    }
//}

enum class DebugMode {
    NONE, DIRECT, INDIRECT
}


fun getPlLanguage(): Language = PgDialect.INSTANCE

fun plNull(value: String) = (value.uppercase() == "NULL")

fun console(msg: String, throwable: Throwable? = null) {
    if (CONSOLE) {
        println(msg)
        throwable?.printStackTrace()
    }
}

fun getSettings(): PlPluginSettings {
    return PlDebuggerSettingsState.getInstance().state
}

fun getAuxiliaryConnection(
    project: Project,
    connectionPoint: DatabaseConnectionPoint,
    searchPath: SearchPath?
): GuardedRef<DatabaseConnection> {
    return DatabaseSessionManager.getFacade(
        project,
        connectionPoint,
        null,
        searchPath,
        true,
        object : ErrorHandler() {},
        DGDepartment.DEBUGGER
    ).connect()
}


/**
 *
 *@param sql
 */
fun sanitizeQuery(sql: String): String {
    var res = sql.trimIndent().replace("(?m)^\\s+\$", "").removeSuffix(";")
    if (res.lowercase().startsWith("select")) {
        res = String.format("(%s)q", res)
    }
    return res
}

fun unquote(s: String): String = s.removeSurrounding("\"")

interface Debugger {
    fun registerConnectionPoint(
        project: Project,
        connectionPoint: DatabaseConnectionPoint,
        searchPath: SearchPath?
    )
    fun internal(): PostgresLib?
    fun user(): PostgresLib?
}

class DebuggerImpl() : Debugger {

    private var jdbi: Jdbi? = null

    private var internal: PostgresLib? = null

    private var user: PostgresLib? = null

    companion object
    {
        val loader = Class.forName("org.postgresql.Driver")
    }

    override fun registerConnectionPoint(
        project: Project,
        connectionPoint: DatabaseConnectionPoint,
        searchPath: SearchPath?
    ) {
        val credentials = DatabaseCredentials.getInstance().getCredentials(connectionPoint)
        jdbi = credentials?.let { cred ->
            cred.password?.run {
                Jdbi.create(connectionPoint.dataSource.url, cred.userName, this.toString(true))
                    .installPlugin(PostgresPlugin())
                    .installPlugin(KotlinPlugin())
                    .installPlugin(KotlinSqlObjectPlugin())
            }
        }
        jdbi?.run {
            this.open().apply {
                internal = this.attach(PostgresLib::class.java)
                this.execute("SET application_name = '$DBG_INTERNAL_APP_NAME';")
//                val query = searchPath?.elements?.joinToString(",") { it.name }
//                query.let {
//                    this.execute("SET search_path = $query;")
//                }
            }
            this.open().apply {
                user = this.attach(PostgresLib::class.java)
                this.execute("SET application_name = '$DBG_USER_APP_NAME';")
            }
        }
    }

    override fun internal(): PostgresLib? {
        return internal
    }

    override fun user(): PostgresLib? {
        return user
    }

}

fun debugger(): Debugger {
    return ApplicationManager.getApplication().getService(Debugger::class.java)
}

fun <T> runInternal(block : (PostgresLib) -> T): T? {
    return debugger().internal()?.run {
        block(this)
    }
}

fun <T> runExternal(block : (PostgresLib) -> T): T? {
    //Get the service
    return debugger().user()?.run {
        block(this)
    }
}

//Run a block of code in a coroutine and execute the callback when it's done
fun <T> runInternalAsync(block : (PostgresLib) -> T, callback: (T) -> Unit) {
    //Get the service
    val debugger = debugger()
    val internal = debugger.internal()
    val user = debugger.user()
    if (internal != null && user != null) {
        //Run the block in a coroutine
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = block(internal)
            callback(result)
        }
    }
}


fun startSession(session: PostgresLib.DebugSession, callback: (session: PostgresLib.DebugSession) -> Unit) {
    runInternalAsync({ PostgresLib.DebugSession(it.waitForTarget(session)) }, callback)
}

fun refreshStack(project: Project, session: PostgresLib.DebugSession, stack: PluginStack) {
    runInternal {lib ->
        val frames = lib.getStack(session)
        frames.forEach {
            val function = lib.getFunction(PostgresLib.Oid(it.func))
            val file = PluginFile(project, function)
            runInEdt {
                pluginFiles().add(file)
                showFile(project, file)
            }

        }
        stack.updateFrames(frames)
    }
}








