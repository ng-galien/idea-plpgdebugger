/*
 * Copyright (c) 2022. Alexandre Boyer
 */




--SELECT plpgsql_oid_debug(16442);
--SELECT * FROM pldbg_wait_for_breakpoint(1);
SHOW shared_preload_libraries;
-- plugin_debugger
SELECT
    roles.oid as id, roles.rolname as name,
    roles.rolsuper as is_superuser,
    CASE WHEN roles.rolsuper THEN true ELSE roles.rolcreaterole END as
        can_create_role,
    CASE WHEN roles.rolsuper THEN true
         ELSE roles.rolcreatedb END as can_create_db,
    CASE WHEN 'pg_signal_backend'=ANY(ARRAY(WITH RECURSIVE cte AS (
        SELECT pg_roles.oid,pg_roles.rolname FROM pg_roles
        WHERE pg_roles.oid = roles.oid
        UNION ALL
        SELECT m.roleid,pgr.rolname FROM cte cte_1
                                             JOIN pg_auth_members m ON m.member = cte_1.oid
                                             JOIN pg_roles pgr ON pgr.oid = m.roleid)
                                            SELECT rolname  FROM cte)) THEN True
         ELSE False END as can_signal_backend
FROM
    pg_catalog.pg_roles as roles
WHERE
        rolname = current_user;

--DEBUG SESSION
--Start debugging
SELECT * FROM pldbg_create_listener();
--return session id
SELECT * FROM pldbg_set_global_breakpoint(1, 16442, -1, NULL);
--return true
SELECT * FROM pg_backend_pid(); --136
-- returns proxy session id
SELECT *
FROM pldbg_wait_for_target(1) w;
--Return caller session pid 40
SELECT * FROM pldbg_get_stack(1);
SELECT * FROM pldbg_step_over(1);
SELECT * FROM pldbg_continue(1);
SELECT pldbg_abort_target(1);




