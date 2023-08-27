package net.plpgsql.ideadebugger

import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.statement.SqlQuery

/**
 * PostgresLib is a JDBI interface to various Postgres functions used by the debugger.
 */
interface PostgresLib: SqlObject {
    /**
     * Oid is a PostgresSQL object identifier.
     */
    data class Oid(val value: Long)

    /**
     * DebugSession is a handle to a debug session.
     */
    data class DebugSession(val value: Int)

    /**
     * Extension is a PostgresSQL extension.
     */
    data class Extension(val namespace: String, val name: String, val version: String)

    /**
     * PostgresSession represents a Postgres session of a connected client.
     */
    data class PostgresSession(val pid: Int, val applicationName: String, val userName: String, val clientAddress: String)

    /**
     * Step is a single step in the execution of a function.
     */
    data class Step(val func: String, val lineNumber: Int, val md5: String)

    /**
     * BreakpointValue is a breakpoint in a function.
     */
    data class BreakpointValue(val func: String, val lineNumber: Int, val targetName: String)

    /**
     * BreakPointCommand is a command to add or remove a breakpoint.
     */
    data class BreakPointCommand(val session: Int, val function: String, val lineNumber: Int)

    /**
     * Frame is a single frame in the stack.
     */
    data class Frame(val level: Int, val func: Long, val lineNumber: Int, val md5: String)

    data class Function(val oid: Long, val name: String, val namespace: String, val definition: String, val md5: String)

    /**
     * Get shared_preload_libraries setting.
     */
    @SqlQuery("""
        SELECT t_setting.setting
        FROM pg_settings t_setting
        WHERE name = 'shared_preload_libraries'
        """)
    fun getSharedPreloadLibraries(): List<String>

    fun hasDebuggerSharedLibrary(): Boolean =
        getSharedPreloadLibraries()
            .any { it.lowercase().contains(DBG_SHARED_LIBRARY) }

    @SqlQuery("""
        SELECT 
            t_namespace.nspname as namespace,
            t_extension.extname as name,
            t_extension.extversion as version
        FROM pg_extension t_extension
        JOIN pg_namespace t_namespace ON t_extension.extnamespace = t_namespace.oid
        """)
    fun getExtensions(): List<Extension>

    fun hasExtensionInstalled(): Boolean =
        getExtensions()
            .any { it.name.lowercase().contains(DBG_EXTENSION) }


    /**
     * Get the List of Running Postgres Sessions.
     */
    @SqlQuery("""
        SELECT t_activity.pid,
               t_activity.application_name,
               t_activity.usename,
               t_activity.client_addr
        FROM pg_stat_activity t_activity
        WHERE t_activity.pid <> pg_backend_pid()
        """)
    fun sessionForApplication(applicationName: String): List<PostgresSession>

    /**
     * Terminate the given backend pid.
     */
    @SqlQuery("""
        SELECT pg_terminate_backend(:session.pid);
        """)
    fun terminateBackend(session: PostgresSession): Boolean

    /**
     * Create a new debug session.
     */
    @SqlQuery("""
        SELECT pldbg_create_listener();
        """)
    fun createListener(): Int

    /**
     * Wait for a new session to connect to the session.
     */
    @SqlQuery("""
        SELECT pldbg_wait_for_target(:session.value);
        """)
    fun waitForTarget(session: DebugSession): Int

    /**
     * Abort the current session.
     */
    @SqlQuery("""
        SELECT * FROM pldbg_abort_target(:session.value)
        """)
    fun abortTarget(debugSession: DebugSession): List<Int>

    /**
     * Step over the current statement.
     */
    @SqlQuery(
        """
        SELECT t_step.func,
               t_step.linenumber,
               md5(pg_catalog.pg_get_functiondef(t_step.func))
            FROM pldbg_step_over(:session.value) t_step
        """
    )
    fun stepOver(session: DebugSession): List<Step>

    /**
     * Step into the current statement.
     */
    @SqlQuery(
        """
        SELECT t_step.func,
               t_step.linenumber,
               md5(pg_catalog.pg_get_functiondef(t_step.func))
        FROM pldbg_step_into(:session.value) t_step
        """
    )
    fun stepInto(session: DebugSession): List<Step>

    /**
     * Continue execution until the next breakpoint.
     */
    @SqlQuery(
        """
        SELECT t_step.func,
               t_step.linenumber,
               md5(pg_catalog.pg_get_functiondef(t_step.func))
        FROM pldbg_continue(:session.value) t_step
        """
    )
    fun continueExecution(session: DebugSession): List<Step>

    @SqlQuery(
        """
        SELECT pldbg_set_global_breakpoint(:session.value, :function.value, -1, NULL)
        """
    )
    fun setGlobalBreakpoint(session: DebugSession, function: Oid): Boolean

    /**
     * Get all breakpoints for the given function.
     */
    @SqlQuery(
        """
        SELECT t_breakpoint.func,
               t_breakpoint.linenumber,
               t_breakpoint.targetname
        FROM pldbg_get_breakpoints(:oid.value) t_breakpoint
        """
    )
    fun getBreakpoints(oid: Oid): List<BreakpointValue>

    /**
     * Add a breakpoint to the given function.
     */
    @SqlQuery("""
        SELECT pldbg_set_breakpoint(:breakpoint.session, :breakpoint.function, :breakpoint.lineNumber)
        """)
    fun addBreakpoint(breakPoint: BreakPointCommand): Boolean

    /**
     * Remove a breakpoint from the given function.
     */
    @SqlQuery("""
        SELECT pldbg_drop_breakpoint(:breakpoint.session, :breakpoint.function, :breakpoint.lineNumber)
        """)
    fun removeBreakpoint(breakPoint: BreakPointCommand): Boolean

    /**
     * Get the current stack as a list of frames.
     */
    @SqlQuery("""
        SELECT 
            t_frame.level as level,
            t_frame.func as func,
            t_frame.linenumber as linenumber,
            md5(pg_catalog.pg_get_functiondef(t_frame.func))
        FROM pldbg_get_stack(:session.value) t_frame
        """)
    fun getStack(session: DebugSession): List<Frame>

    @SqlQuery("""
        SELECT t_proc.oid as oid,
               t_namespace.nspname as namespace,
               t_proc.proname as name,
               pg_catalog.pg_get_functiondef(t_proc.oid) as definition,
               md5(pg_catalog.pg_get_functiondef(t_proc.oid)) as md5
        FROM pg_proc t_proc
                 JOIN pg_namespace t_namespace on t_proc.pronamespace = t_namespace.oid
        WHERE t_proc.oid = :oid.value
        """)
    fun getFunction(oid: Oid): Function

    data class StackValue(
        val isArg: Boolean,
        val line: Int,
        val oid: Long,
        val name: String,
        val type: String,
        val kind: String,
        val isArray: Boolean,
        val isText: Boolean,
        val arrayType: String,
        val value: String,
        val pretty: String
    ) {
        fun isNull(): Boolean {
            return value == "NULL"
        }

        fun isComposite(): Boolean {
            return kind == "c"
        }
        fun isJSON() = type in listOf("json", "jsonb", "record", "jsonArray")
    }

    @SqlQuery(
        """
            SELECT
            varclass = 'A' as is_arg,
            linenumber as line,
            t_type.oid as oid,
            t_var.name as name,
            coalesce(t_type.oid::regtype::TEXT, 'text') as type,
            coalesce(t_type.typtype, 'b') as kind,
            t_type.typarray = 0 as is_array,
            t_type.typcategory = 'S' as is_text,
            coalesce(t_sub.oid::regtype::TEXT, 'text') as array_type,
            t_var.value as value,
            '' as pretty
        FROM pldbg_get_variables(:session.value) t_var
             LEFT JOIN pg_type t_type ON t_var.dtype = t_type.oid
             LEFT JOIN pg_type t_sub ON t_type.typelem = t_sub.oid;
        """
    )
    fun stackValues(session: DebugSession): List<StackValue>

    @SqlQuery(
        """
        SELECT t_arr_type.oid                          AS oid,
               '%s[' || idx || ']'                     AS name,
               t_arr_type.typname                      AS type,
               t_arr_type.typtype                      AS kind,
               t_arr_type.typarray = 0                 AS is_array,
               t_arr_type.typcategory = 'S'            AS is_text,
               t_sub.typname                           AS array_type,
               coalesce(arr.val::TEXT, 'NULL')         AS value,
               coalesce(jsonb_pretty(arr.val), 'NULL') AS pretty
        FROM jsonb_array_elements(:json) WITH ORDINALITY arr(val, idx)
                 JOIN pg_type t_type ON t_type.oid = :oid.value
                 JOIN pg_type t_arr_type ON t_type.typelem = t_arr_type.oid
                 LEFT JOIN pg_type t_sub ON t_arr_type.typelem = t_sub.oid;
        """)
    fun stackArrayValues(oid: Oid, json: String): List<StackValue>

    @SqlQuery("""
        SELECT t_att_type.oid                                                               AS oid,
               t_att.attname                                                                AS name,
               t_att_type.typname                                                           AS type_name,
               t_att_type.typtype                                                           AS kind,
               t_att_type.typarray = 0                                                      AS is_array,
               t_att_type.typcategory = 'S'                                                 AS is_text,
               coalesce(t_sub.typname, 'unknown')                                           AS array_type,
               coalesce(jsonb_extract_path_text(jsonb.val, t_att.attname), 'NULL')          AS value,
               coalesce(jsonb_pretty(jsonb_extract_path(jsonb.val, t_att.attname)), 'NULL') AS pretty
        FROM pg_type t_type
                 JOIN pg_class t_class
                      ON t_type.typrelid = t_class.oid
                 JOIN pg_attribute t_att
                      ON t_att.attrelid = t_class.oid AND t_att.attnum > 0
                 JOIN pg_type t_att_type
                      ON t_att.atttypid = t_att_type.oid
                 LEFT JOIN pg_type t_sub ON t_att_type.typelem = t_sub.oid
                 JOIN (SELECT (:json)::jsonb val) AS jsonb
                      ON TRUE
        WHERE t_type.oid = :oid.value
        """)
    fun stackCompositeValues(oid: Oid, json: String): List<StackValue>

}