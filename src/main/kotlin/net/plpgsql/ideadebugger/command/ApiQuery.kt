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

package net.plpgsql.ideadebugger.command

import net.plpgsql.ideadebugger.DEBUGGER_SESSION_NAME
import net.plpgsql.ideadebugger.Producer
import net.plpgsql.ideadebugger.SELECT_NULL

/**
 * API queries for the PL/pgSQL debugger.
 *
 * This enum defines all the SQL queries used by the debugger to interact with the PostgreSQL database.
 * Each enum value represents a specific query operation with its corresponding SQL statement and result producer.
 *
 * @property sql The SQL query to be executed.
 * @property producer The producer that converts the query result to a specific data type.
 * @property disableDecoration Whether to disable SQL decoration for this query.
 * @property print Whether to print the query execution in the debug console.
 */
enum class ApiQuery(val sql: String,
                    val producer: Producer<Any>,
                    val disableDecoration: Boolean = false,
                    val print: Boolean = true) {
    /**
     * A void query that does nothing.
     * Used as a placeholder or when no operation is needed.
     */
    VOID(
        SELECT_NULL,
        Producer { PlApiVoid() }),

    /**
     * Executes a raw SQL query without returning any result.
     * The query is passed as a parameter and executed as-is without decoration.
     */
    RAW_VOID(
        "%s",
        Producer { PlApiVoid() },
        true
    ),

    /**
     * Executes a raw SQL query and returns a boolean result.
     * Used for simple boolean operations.
     */
    RAW_BOOL(
        "%s",
        Producer { PlApiBoolean(it.bool()) }),

    /**
     * Retrieves information about the current debugging sessions.
     * Returns active PostgreSQL sessions with the debugger application name.
     */
    PG_ACTIVITY(
        """
        SELECT pid,
               application_name,
               usename,
               client_addr
        FROM pg_stat_activity
        WHERE application_name = '$DEBUGGER_SESSION_NAME'
        AND pid <> pg_backend_pid();
        """,
        Producer { PlActivity(it.long(), it.string(), it.string(), it.string()) }),

    /**
     * Terminates a PostgreSQL backend session.
     * Used to cancel a debugging session.
     */
    PG_CANCEL(
        """
        SELECT pg_terminate_backend(%s);
        """,
        Producer { PlApiBoolean(it.bool()) }
    ),

    /**
     * Creates a debugger listener in the PostgreSQL server.
     * This is the first step in establishing a debugging session.
     */
    CREATE_LISTENER(
        "pldbg_create_listener()",
        Producer { PlApiInt(it.int()) }),

    /**
     * Waits for a target function to be executed in debug mode.
     * Blocks until a function is called with debugging enabled.
     */
    WAIT_FOR_TARGET(
        "pldbg_wait_for_target(%s)",
        Producer { PlApiInt(it.int()) }),

    /**
     * Aborts the current debugging target.
     * Terminates the current debugging session.
     */
    ABORT(
        "pldbg_abort_target(%s)",
        Producer { PlApiBoolean(it.bool()) }),

    /**
     * Steps over the current line in the debugged function.
     * Returns the new position (function OID, line number) and a hash of the function source.
     */
    STEP_OVER(
        """
            SELECT step.func,
                   step.linenumber,
                   md5(pg_catalog.pg_get_functiondef(step.func))
            FROM pldbg_step_over(%s) step;
        """,
        Producer { PlApiStep(it.long(), it.int(), it.string()) }),

    /**
     * Steps into a function call at the current line.
     * Returns the new position (function OID, line number) and a hash of the function source.
     */
    STEP_INTO(
        """
            SELECT step.func,
                   step.linenumber,
                   md5(pg_catalog.pg_get_functiondef(step.func))
            FROM pldbg_step_into(%s) step;
        """,
        Producer { PlApiStep(it.long(), it.int(), it.string()) }),

    /**
     * Continues execution until the next breakpoint or the end of the function.
     * Returns the new position (function OID, line number) and a hash of the function source.
     */
    STEP_CONTINUE(
        """
            SELECT step.func,
                   step.linenumber,
                   md5(pg_catalog.pg_get_functiondef(step.func))
            FROM pldbg_continue(%s) step;
        """,
        Producer { PlApiStep(it.long(), it.int(), it.string()) }),

    /**
     * Lists all breakpoints in the current debugging session.
     * Returns the function OID and line number for each breakpoint.
     */
    LIST_BREAKPOINT(
        """
            SELECT bp.func,
                   bp.linenumber,
                   ''
            FROM pldbg_get_breakpoints(%s) bp;
        """,
        Producer { PlApiStep(it.long(), it.int(), it.string()) }),

    /**
     * Sets a global breakpoint for a specific function.
     * Global breakpoints are triggered whenever the function is called.
     */
    SET_GLOBAL_BREAKPOINT(
        "pldbg_set_global_breakpoint(%s, %s, -1, NULL)",
        Producer { PlApiBoolean(it.bool()) }),

    /**
     * Sets a breakpoint at a specific line in a function.
     * Returns true if the breakpoint was successfully set.
     */
    SET_BREAKPOINT(
        "pldbg_set_breakpoint(%s, %s, %s)",
        Producer { PlApiBoolean(it.bool()) }),

    /**
     * Removes a breakpoint from a specific line in a function.
     * Returns true if the breakpoint was successfully removed.
     */
    DROP_BREAKPOINT(
        "pldbg_drop_breakpoint(%s, %s, %s)",
        Producer { PlApiBoolean(it.bool()) }),

    /**
     * Retrieves the current call stack of the debugged function.
     * Returns information about each stack frame including function OID, line number, and source hash.
     */
    GET_STACK(
        """
        SELECT 
            frame.level,
            frame.func,
            frame.linenumber,
            md5(pg_catalog.pg_get_functiondef(frame.func))
        FROM pldbg_get_stack(%s) frame;
        """,
        Producer {
            PlApiStackFrame(
                it.int(), // Level
                it.long(), // Oid
                it.int(),
                it.string(),
            )
        }
    ),

    /**
     * Retrieves all variables in the current stack frame.
     * Joins with pg_type to get detailed type information for each variable.
     * Returns variable information including name, type, value, and metadata.
     */
    GET_RAW_VARIABLES(
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
        FROM pldbg_get_variables(%s) t_var
             LEFT JOIN pg_type t_type ON t_var.dtype = t_type.oid
             LEFT JOIN pg_type t_sub ON t_type.typelem = t_sub.oid;
        """, Producer {
            PlApiStackVariable(
                it.bool(),
                it.int(),
                PlApiValue(
                    it.long(),
                    it.string(),
                    it.string(),
                    it.char(),
                    it.bool(),
                    it.bool(),
                    it.string(),
                    it.string(),
                    it.string()
                )
            )
        }
    ),

    /**
     * Retrieves variables in JSON format.
     * Used for custom variable queries with JSON output.
     * The SQL query is provided as a parameter.
     */
    GET_JSON_VARIABLES(
        sql = "%s",
        producer = Producer {
            PlApiStackVariable(
                it.bool(),
                it.int(),
                PlApiValue(
                    it.long(),
                    it.string(),
                    it.string(),
                    it.char(),
                    it.bool(),
                    it.bool(),
                    it.string(),
                    it.string(),
                    it.string()
                )
            )
        },
        print = false
    ),

    /**
     * Retrieves the list of shared libraries loaded in PostgreSQL.
     * Used to check if the debugger extension is properly loaded.
     */
    GET_SHARED_LIBRARIES(
        sql = """
        SELECT setting
        FROM pg_settings
        WHERE name = 'shared_preload_libraries'
        """.trimIndent(),
        producer = Producer {
            PlApiString(it.string())
        },
        disableDecoration = true
    ),

    /**
     * Retrieves information about installed PostgreSQL extensions.
     * Used to check if the debugger extension is installed.
     * Returns the namespace, extension name, and version.
     */
    GET_EXTENSION(
        """
        SELECT 
            t_namespace.nspname,
            t_extension.extname,
            t_extension.extversion
        FROM pg_extension t_extension
        JOIN pg_namespace t_namespace ON t_extension.extnamespace = t_namespace.oid
        """,
        Producer {
            PlApiExtension(it.string(), it.string(), it.string())
        }
    ),

    /**
     * Retrieves the arguments of a function by schema and name.
     * Used to get information about function parameters for debugging.
     * Returns detailed information about each argument including name, type, and default value status.
     */
    GET_FUNCTION_CALL_ARGS(
        """
        SELECT 
               t_proc2.oid,
               t_proc2.pronargs,
               t_proc2.idx,
               t_proc2.proargname,
               concat(t_type_ns.nspname, '.', t_type.typname),
               t_proc2.pronargs > 0 AND idx > (t_proc2.pronargs - t_proc2.pronargdefaults)
        FROM (SELECT idx as idx,
                     t_proc1.pronargs,
                     t_proc1.pronargdefaults,
                     t_proc1.oid,
                     t_proc1.proargtypes[idx - 1] AS proargtype,
                     t_proc1.proargnames[idx]     AS proargname
              FROM (SELECT t_proc.oid,
                           CASE WHEN t_proc.pronargs = 0 THEN '{0}'::oid[] ELSE t_proc.proargtypes::oid[] END AS proargtypes,
                           CASE WHEN t_proc.pronargs = 0 THEN '{""}'::TEXT[] ELSE t_proc.proargnames END      AS proargnames,
                           t_proc.pronargs,
                           t_proc.pronargdefaults,
                           CASE WHEN t_proc.pronargs = 0 THEN 1 ELSE t_proc.pronargs END                      AS serial
                    FROM pg_proc t_proc
                             JOIN pg_namespace t_namespace
                                  ON t_proc.pronamespace = t_namespace.oid
                    WHERE lower(t_namespace.nspname) = lower('%s')
                      AND lower(t_proc.proname) = lower('%s')
                    ORDER BY t_proc.oid) t_proc1,
                   generate_series(1, t_proc1.serial) idx) t_proc2
                 LEFT JOIN pg_type t_type
                           ON t_proc2.proargtype = t_type.oid
                 LEFT JOIN pg_namespace t_type_ns ON t_type.typnamespace = t_type_ns.oid;
        """, Producer {
            PlApiFunctionArg(
                it.long(),
                it.int(),
                it.int(),
                it.string(),
                it.string(),
                it.bool(),
            )
        }
    ),

    /**
     * Retrieves the definition of a function by its OID.
     * Used to get the source code of a function for debugging.
     * Returns the function OID, schema, name, definition, and a hash of the definition.
     */
    GET_FUNCTION_DEF(
        """
        SELECT t_proc.oid,
               t_namespace.nspname,
               t_proc.proname,
               pg_catalog.pg_get_functiondef(t_proc.oid),
               md5(pg_catalog.pg_get_functiondef(t_proc.oid))
        FROM pg_proc t_proc
                 JOIN pg_namespace t_namespace on t_proc.pronamespace = t_namespace.oid
        WHERE t_proc.oid = %s;
        """, Producer {
            PlApiFunctionDef(
                it.long(),
                it.string(),
                it.string(),
                "${it.string().removeSuffix("\n")};",
                it.string()
            )
        }
    ),

    /**
     * Executes a custom query to explode (expand) a complex value.
     * Used for inspecting complex variable values during debugging.
     * The SQL query is provided as a parameter.
     */
    EXPLODE("%s", Producer {
        PlApiValue(
            it.long(),
            it.string(),
            it.string(),
            it.char(),
            it.bool(),
            it.bool(),
            it.string(),
            it.string(),
            it.string()
        )
    }),

    /**
     * Explodes (expands) an array value into its individual elements.
     * Used for inspecting array variables during debugging.
     * Returns detailed information about each array element including type, value, and metadata.
     * 
     * @param varName The name of the variable to use in the result
     * @param jsonValue The JSON representation of the array
     * @param typeOid The OID of the array type
     */
    EXPLODE_ARRAY(
        """
        SELECT t_arr_type.oid                          AS oid,
               '%s[' || idx || ']'                     AS name,
               t_arr_type.typname                      AS type,
               t_arr_type.typtype                      AS kind,
               t_arr_type.typarray = 0                 AS is_array,
               t_arr_type.typcategory = 'S'            AS is_text,
               coalesce(t_sub.typname, 'unknown')      AS array_type,
               coalesce(arr.val::TEXT, 'NULL')         AS value,
               coalesce(jsonb_pretty(arr.val), 'NULL') AS pretty
        FROM jsonb_array_elements('%s'::jsonb) WITH ORDINALITY arr(val, idx)
                 JOIN pg_type t_type ON t_type.oid = %s
                 JOIN pg_type t_arr_type ON t_type.typelem = t_arr_type.oid
                 LEFT JOIN pg_type t_sub ON t_arr_type.typelem = t_sub.oid;
        """,
        Producer {
            PlApiValue(
                it.long(),
                it.string(),
                it.string(),
                it.char(),
                it.bool(),
                it.bool(),
                it.string(),
                it.string(),
                it.string()
            )
        }
    ),

    /**
     * Explodes (expands) a composite type value into its individual fields.
     * Used for inspecting composite variables (records, custom types) during debugging.
     * Returns detailed information about each field including name, type, value, and metadata.
     * 
     * @param jsonValue The JSON representation of the composite value
     * @param typeOid The OID of the composite type
     */
    EXPLODE_COMPOSITE(
        """
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
                 JOIN (SELECT '%s'::jsonb val) AS jsonb
                      ON TRUE
        WHERE t_type.oid = %s;
        """, Producer {
            PlApiValue(
                it.long(),
                it.string(),
                it.string(),
                it.char(),
                it.bool(),
                it.bool(),
                it.string(),
                it.string(),
                it.string()
            )
        }
    ),
}
