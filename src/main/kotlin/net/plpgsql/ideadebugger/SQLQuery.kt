/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

enum class SQLQuery(val sql: String, val producer: Producer<Any>) {
    RAW_BOOL(
        "%s",
        Producer<Any> { PlBoolean(it.bool()) }),
    CREATE_LISTENER(
        "pldbg_create_listener()",
        Producer<Any> { PlInt(it.int()) }),
    ATTACH_TO_PORT(
        "pldbg_attach_to_port(%s)",
        Producer<Any> { PlInt(it.int()) }),
    ABORT(
        "pldbg_abort_target(%s)",
        Producer<Any> { PlBoolean(it.bool()) }),
    DEBUG_OID(
        "plpgsql_oid_debug(%s)",
        Producer<Any> { PlInt(it.int()) }),

    STEP_OVER(
        "pldbg_step_over(%s)",
        Producer<Any> { PlStep(it.long(), it.int(), it.string()) }),
    STEP_INTO(
        "pldbg_step_into(%s)",
        Producer<Any> { PlStep(it.long(), it.int(), it.string()) }),
    STEP_CONTINUE(
        "pldbg_continue(%s)",
        Producer<Any> { PlStep(it.long(), it.int(), it.string()) }),

    LIST_BREAKPOINT(
        "pldbg_get_breakpoints(%s)",
        Producer<Any> { PlStackBreakPoint(it.long(), it.int()) }),
    ADD_BREAKPOINT(
        "pldbg_set_breakpoint(%s, %s, %s)",
        Producer<Any> { PlBoolean(it.bool()) }),
    DROP_BREAKPOINT(
        "pldbg_drop_breakpoint(%s, %s, %s)",
        Producer<Any> { PlBoolean(it.bool()) }),

    GET_STACK(
        "pldbg_get_stack(%s)",
        Producer<Any> {
            PlStackFrame(
                it.int(),
                it.string(),
                it.long(),
                it.int(),
                it.string()
            )
        }
    ),

    GET_VARIABLES(
        """
        (SELECT
               varclass = 'A' as is_arg,
               linenumber as line,
               t_type.oid as oid,
               t_var.name as name,
               coalesce(t_type.typname, 'unknown') as type,
               coalesce(t_type.typtype, 'b') as kind,
               t_type.typarray = 0 as is_array,
               coalesce(t_sub.typname, 'unknown') as array_type,
               t_var.value as value
        FROM pldbg_get_variables(%s) t_var
        LEFT JOIN pg_type t_type ON t_var.dtype = t_type.oid
        LEFT JOIN pg_type t_sub ON t_type.typelem = t_sub.oid) v
        """, Producer<Any> {
            PlStackVariable(
                it.bool(),
                it.int(),
                PlValue(
                    it.long(),
                    it.string(),
                    it.string(),
                    it.char(),
                    it.bool(),
                    it.string(),
                    it.string()
                )
            )
        }
    ),

    GET_EXTENSION(
        """
            SELECT 
                t_namespace.nspname,
                t_extension.extname,
                t_extension.extversion
            FROM pg_extension t_extension
            JOIN pg_namespace t_namespace ON t_extension.extnamespace = t_namespace.oid
        """, Producer<Any> {
            PlExtension(it.string(), it.string(), it.string())
        }
    ),
    GET_FUNCTION_CALL_ARGS(
        """
        (SELECT t_proc.oid,
               pos,
               t_proc.proargnames[pos],
               t_type.typname,
               pos > (t_proc.pronargs - t_proc.pronargdefaults)
        FROM pg_proc t_proc
                 JOIN pg_namespace t_namespace ON t_proc.pronamespace = t_namespace.oid,
             unnest(t_proc.proargtypes) WITH ORDINALITY arg(el, pos)
                 JOIN pg_type t_type ON el = t_type.oid
        WHERE t_namespace.nspname LIKE '%s'
          AND t_proc.proname LIKE '%s'
        ORDER BY t_proc.oid, pos) a
        """, Producer<Any> {
            PlFunctionArg(
                it.long(),
                it.int(),
                it.string(),
                it.string(),
                it.bool(),
            )
        }
    ),

    GET_FUNCTION_DEF(
        """
        (SELECT t_proc.oid,
               t_namespace.nspname,
               t_proc.proname,
               pg_catalog.pg_get_functiondef(t_proc.oid)
        FROM pg_proc t_proc
                 JOIN pg_namespace t_namespace on t_proc.pronamespace = t_namespace.oid
        WHERE t_proc.oid = %s) f
        """, Producer<Any> {
            PlFunctionDef(
                it.long(),
                it.string(),
                it.string(),
                it.string()
            )
        }
    ),

    EXPLODE("%s", Producer<Any> {
        PlValue(
            it.long(),
            it.string(),
            it.string(),
            it.char(),
            it.bool(),
            it.string(),
            it.string()
        )
    }),

    EXPLODE_ARRAY(
        """
        (SELECT 
               t_arr_type.oid                     AS oid,
               '%s[' || idx || ']'                AS name,
               t_arr_type.typname                 AS type,
               t_arr_type.typtype                 AS kind,
               t_arr_type.typarray = 0            AS is_array,
               coalesce(t_sub.typname, 'unknown') AS array_type,
               arr.val::TEXT                      AS value
        FROM jsonb_array_elements_text('%s'::jsonb) WITH ORDINALITY arr(val, idx)
                 JOIN pg_type t_type ON t_type.oid = %s
                 JOIN pg_type t_arr_type ON t_type.typelem = t_arr_type.oid
                 LEFT JOIN pg_type t_sub ON t_arr_type.typelem = t_sub.oid) f
        """, Producer<Any> {
            PlValue(
                it.long(),
                it.string(),
                it.string(),
                it.char(),
                it.bool(),
                it.string(),
                it.string()
            )
        }
    ),

    EXPLODE_COMPOSITE(
        """
        (SELECT 
               t_att_type.oid                                   AS oid,
               t_att.attname                                    AS name,
               t_att_type.typname                               AS type_name,
               t_att_type.typtype                               AS kind,
               t_att_type.typarray = 0                          AS is_array,
               coalesce(t_sub.typname, 'unknown')               AS array_type,
               jsonb_extract_path_text(jsonb.val, t_att.attname) AS value
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
        WHERE t_type.oid = %s) c
        """, Producer<Any> {
            PlValue(
                it.long(),
                it.string(),
                it.string(),
                it.char(),
                it.bool(),
                it.string(),
                it.string()
            )
        }
    ),

    T0_JSON(
        """
        (SELECT to_jsonb(row) FROM (SELECT %s::%s) row) j
        """, Producer<Any> { PlString(it.string()) }
    ),

    OLD_FUNCTION(
        """
            SELECT func.id 
            FROM unnest(%s) WITH ORDINALITY func(id)
            LEFT JOIN pg_proc t_proc ON t_proc.oid = func.id
            WHERE t_proc IS NULL
        """, Producer<Any> { PlLong(it.long()) }
    )
}
