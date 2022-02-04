/*
 * Copyright (c) 2022. Alexandre Boyer
 */

SELECT t_proc2.oid,
       t_proc2.pronargs,
       t_proc2.idx,
       t_proc2.proargname,
       t_proc2.proargmodes,
       concat(t_type_ns.nspname, '.', t_type.typname),
       t_proc2.pronargs > 0 AND idx > (t_proc2.pronargs - t_proc2.pronargdefaults)
FROM (SELECT idx                          AS idx,
             t_proc1.pronargs,
             t_proc1.pronargdefaults,
             t_proc1.oid,
             t_proc1.proargtypes[idx - 1] AS proargtype,
             t_proc1.proargmodes[idx]     AS proargmodes,
             t_proc1.proargnames[idx]     AS proargname
      FROM (SELECT t_proc.oid,
                   CASE
                       WHEN array_length(t_proc.proallargtypes, 1) = 0
                           THEN '{0}'::oid[]
                       ELSE t_proc.proallargtypes::oid[]
                       END                                             AS proargtypes,
                   CASE
                       WHEN array_length(t_proc.proallargtypes, 1) = 0
                           THEN '{u}'::CHAR[]
                       ELSE t_proc.proargmodes::CHAR[]
                       END                                             AS proargmodes,
                   CASE
                       WHEN array_length(t_proc.proallargtypes, 1) = 0
                           THEN '{""}'::TEXT[]
                       ELSE t_proc.proargnames::TEXT[] END             AS proargnames,
                   array_length(t_proc.proallargtypes, 1)              AS pronargs,
                   t_proc.pronargdefaults,
                   CASE
                       WHEN array_length(t_proc.proallargtypes, 1) = 0
                           THEN 1
                       ELSE array_length(t_proc.proallargtypes, 1) END AS serial
            FROM pg_proc t_proc
                     JOIN pg_namespace t_namespace
                          ON t_proc.pronamespace = t_namespace.oid
            WHERE lower(t_namespace.nspname) = lower('public')
              AND lower(t_proc.proname) = lower('test_debug_out')
            ORDER BY t_proc.oid) t_proc1,
           generate_series(1, t_proc1.serial) idx) t_proc2
         LEFT JOIN pg_type t_type
                   ON t_proc2.proargtype = t_type.oid
         LEFT JOIN pg_namespace t_type_ns ON t_type.typnamespace = t_type_ns.oid;