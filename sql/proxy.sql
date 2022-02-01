/*
 * Copyright (c) 2022. Alexandre Boyer
 */

WITH RECURSIVE cte AS (
    SELECT oid
    FROM pg_roles
    WHERE rolname = session_user
    UNION ALL
    SELECT m.roleid
    FROM cte
             JOIN pg_auth_members m ON m.member = cte.oid
)
SELECT oid, oid::regrole::TEXT AS rolename
FROM cte;

SELECT * FROM
(WITH RECURSIVE cte AS (
    SELECT oid
    FROM pg_roles
    WHERE rolname = current_user
    UNION ALL
    SELECT m.roleid
    FROM cte
             JOIN pg_auth_members m ON m.member = cte.oid
)
SELECT current_user,
       count(*) = 2
FROM information_schema.routine_privileges t_priviliege
WHERE t_priviliege.routine_name IN ('pg_terminate_backend', 'pg_cancel_backend')
  AND t_priviliege.grantee IN (SELECT oid::regrole::TEXT AS rolename FROM cte)
  AND t_priviliege.privilege_type = 'EXECUTE') g;
--SELECT plpgsql_oid_debug(16442);
--SELECT * FROM pldbg_wait_for_breakpoint(1);
SHOW shared_preload_libraries;
-- plugin_debugger
SELECT roles.oid                                                       AS id,
       roles.rolname                                                   AS name,
       roles.rolsuper                                                  AS is_superuser,
       CASE WHEN roles.rolsuper THEN TRUE ELSE roles.rolcreaterole END AS
                                                                          can_create_role,
       CASE
           WHEN roles.rolsuper THEN TRUE
           ELSE roles.rolcreatedb END                                  AS can_create_db,
       CASE
           WHEN 'pg_terminate_backend' = ANY (array(WITH RECURSIVE cte AS (
               SELECT pg_roles.oid, pg_roles.rolname
               FROM pg_roles
               WHERE pg_roles.oid = roles.oid
               UNION ALL
               SELECT m.roleid, pgr.rolname
               FROM cte cte_1
                        JOIN pg_auth_members m ON m.member = cte_1.oid
                        JOIN pg_roles pgr ON pgr.oid = m.roleid)
                                                    SELECT rolname
                                                    FROM cte)) THEN TRUE
           ELSE FALSE END                                              AS can_signal_backend
FROM pg_catalog.pg_roles AS roles
WHERE rolname = current_user;



--DEBUG SESSION
--Start debugging
SELECT *
FROM pldbg_create_listener();

--return session id
SELECT *
FROM pldbg_set_global_breakpoint(1, 16442, -1, NULL);
--return true
SELECT *
FROM pg_backend_pid();
--136
-- returns proxy session id
SELECT *
FROM pldbg_wait_for_target(1) w;
--Return caller session pid 40
SELECT *
FROM pldbg_get_stack(1);
SELECT *
FROM pldbg_step_over(1);
SELECT *
FROM pldbg_continue(1);
SELECT pldbg_abort_target(1);

SELECT exists(SELECT FROM pg_stat_activity WHERE pid = 3);




