/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.command

import net.plpgsql.ideadebugger.DEBUGGER_SESSION_NAME
import net.plpgsql.ideadebugger.Producer
import net.plpgsql.ideadebugger.SELECT_NULL

/**
 *
 */
enum class ApiQuery(val sql: String,
                    val producer: Producer<Any>,
                    val disableDecoration: Boolean = false,
                    val print: Boolean = true) {

    VOID(
        SELECT_NULL,
        Producer<Any> { PlApiVoid() }),
    RAW_VOID(
        "%s",
        Producer<Any> { PlApiVoid() },
        true
    ),
    RAW_BOOL(
        "%s",
        Producer<Any> { PlApiBoolean(it.bool()) }),
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
        Producer<Any> { PlActivity(it.long(), it.string(), it.string(), it.string()) }),
    PG_CANCEL(
        """
        SELECT pg_terminate_backend(%s);
        """,
        Producer<Any> { PlApiBoolean(it.bool()) }
    ),
    CREATE_LISTENER(
        "pldbg_create_listener()",
        Producer<Any> { PlApiInt(it.int()) }),
    WAIT_FOR_TARGET(
        "pldbg_wait_for_target(%s)",
        Producer<Any> { PlApiInt(it.int()) }),
    ABORT(
        "pldbg_abort_target(%s)",
        Producer<Any> { PlApiBoolean(it.bool()) }),
    STEP_OVER(
        """
            SELECT step.func,
                   step.linenumber,
                   md5(pg_catalog.pg_get_functiondef(step.func))
            FROM pldbg_step_over(%s) step;
        """,
        Producer<Any> { PlApiStep(it.long(), it.int(), it.string()) }),
    STEP_INTO(
        """
            SELECT step.func,
                   step.linenumber,
                   md5(pg_catalog.pg_get_functiondef(step.func))
            FROM pldbg_step_into(%s) step;
        """,
        Producer<Any> { PlApiStep(it.long(), it.int(), it.string()) }),
    STEP_CONTINUE(
        """
            SELECT step.func,
                   step.linenumber,
                   md5(pg_catalog.pg_get_functiondef(step.func))
            FROM pldbg_continue(%s) step;
        """,
        Producer<Any> { PlApiStep(it.long(), it.int(), it.string()) }),

    LIST_BREAKPOINT(
        """
            SELECT bp.func,
                   bp.linenumber,
                   ''
            FROM pldbg_get_breakpoints(%s) bp;
        """,
        Producer<Any> { PlApiStep(it.long(), it.int(), it.string()) }),
    SET_GLOBAL_BREAKPOINT(
        "pldbg_set_global_breakpoint(%s, %s, -1, NULL)",
        Producer<Any> { PlApiBoolean(it.bool()) }),
    SET_BREAKPOINT(
        "pldbg_set_breakpoint(%s, %s, %s)",
        Producer<Any> { PlApiBoolean(it.bool()) }),
    DROP_BREAKPOINT(
        "pldbg_drop_breakpoint(%s, %s, %s)",
        Producer<Any> { PlApiBoolean(it.bool()) }),

    GET_STACK(
        """
        SELECT 
            frame.level,
            frame.func,
            frame.linenumber,
            md5(pg_catalog.pg_get_functiondef(frame.func))
        FROM pldbg_get_stack(%s) frame;
        """,
        Producer<Any> {
            PlApiStackFrame(
                it.int(), // Level
                it.long(), // Oid
                it.int(),
                it.string(),
            )
        }
    ),
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
        """, Producer<Any> {
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
    GET_JSON_VARIABLES(
        sql = "%s",
        producer = Producer<Any> {
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

    GET_SHARED_LIBRARIES(
        sql = """
        SELECT setting
        FROM pg_settings
        WHERE name = 'shared_preload_libraries'
        """.trimIndent(),
        producer = Producer<Any> {
            PlApiString(it.string())
        },
        disableDecoration = true
    ),

    GET_EXTENSION(
        """
        SELECT 
            t_namespace.nspname,
            t_extension.extname,
            t_extension.extversion
        FROM pg_extension t_extension
        JOIN pg_namespace t_namespace ON t_extension.extnamespace = t_namespace.oid
        """,
        Producer<Any> {
            PlApiExtension(it.string(), it.string(), it.string())
        }
    ),
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
        """, Producer<Any> {
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
        """, Producer<Any> {
            PlApiFunctionDef(
                it.long(),
                it.string(),
                it.string(),
                "${it.string().removeSuffix("\n")};",
                it.string()
            )
        }
    ),

    EXPLODE("%s", Producer<Any> {
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
        Producer<Any> {
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
        """, Producer<Any> {
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



