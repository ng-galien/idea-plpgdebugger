/*
* Copyright (c) 2022. Alexandre Boyer
*/

2022-01-28 00:28:30.351 UTC [100] LOG:  statement: SHOW shared_preload_libraries
2022-01-28 00:28:30.353 UTC [100] LOG:  statement: SELECT installed_version FROM pg_catalog.pg_available_extensions WHERE name = 'pldbgapi'
2022-01-28 00:28:30.357 UTC [100] LOG:  statement: SELECT
x.oid, pg_catalog.pg_get_userbyid(extowner) AS owner,
x.extname AS name, n.nspname AS schema,
x.extrelocatable AS relocatable, x.extversion AS version,
e.comment
FROM
pg_catalog.pg_extension x
LEFT JOIN pg_catalog.pg_namespace n ON x.extnamespace=n.oid
JOIN pg_catalog.pg_available_extensions() e(name, default_version, comment) ON x.extname=e.name WHERE x.extname = 'pldbgapi'::text

2022-01-28 00:28:30.360 UTC [100] LOG:  statement: SELECT current_setting('search_path')||',public'
2022-01-28 00:28:30.362 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT COUNT(*) FROM pg_catalog.pg_proc p LEFT JOIN pg_catalog.pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ANY(current_schemas(false)) AND p.proname = 'pldbg_get_proxy_info';
2022-01-28 00:28:30.364 UTC [100] LOG:  statement: SELECT proxyapiver FROM pldbg_get_proxy_info();
2022-01-28 00:28:31.120 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT * from pldbg_create_listener()
2022-01-28 00:28:31.135 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT * FROM pldbg_set_global_breakpoint(1, 16384, -1, NULL)
2022-01-28 00:28:31.148 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT * FROM pldbg_wait_for_target(1::int)

2022-01-28 00:28:30.360 UTC [100] LOG:  statement: SELECT current_setting('search_path')||',public'
2022-01-28 00:28:30.362 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT COUNT(*) FROM pg_catalog.pg_proc p LEFT JOIN pg_catalog.pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ANY(current_schemas(false)) AND p.proname = 'pldbg_get_proxy_info';
2022-01-28 00:28:30.364 UTC [100] LOG:  statement: SELECT proxyapiver FROM pldbg_get_proxy_info();
2022-01-28 00:28:31.120 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT * from pldbg_create_listener()
2022-01-28 00:28:31.135 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT * FROM pldbg_set_global_breakpoint(1, 16384, -1, NULL)

2022-01-28 00:28:31.148 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT * FROM pldbg_wait_for_target(1::int)

2022-01-28 00:31:24.450 UTC [303] LOG:  execute <unnamed>: SET extra_float_digits = 3
2022-01-28 00:31:24.452 UTC [303] LOG:  execute <unnamed>: SET application_name = ''
2022-01-28 00:31:24.454 UTC [303] LOG:  execute <unnamed>: select version()
2022-01-28 00:31:24.459 UTC [303] LOG:  execute <unnamed>: SET application_name = 'IntelliJ IDEA 2021.3.1'
2022-01-28 00:31:24.477 UTC [303] LOG:  execute <unnamed>: SHOW TRANSACTION ISOLATION LEVEL
2022-01-28 00:31:24.483 UTC [303] LOG:  execute <unnamed>: set search_path = "public"
2022-01-28 00:31:24.503 UTC [303] LOG:  execute <unnamed>: select current_database() as a, current_schemas(false) as b
2022-01-28 00:31:24.513 UTC [303] LOG:  execute <unnamed>: SELECT * FROM test_debug('sass', 3)
2022-01-28 00:31:24.552 UTC [303] LOG:  execute <unnamed>: SHOW TRANSACTION ISOLATION LEVEL
2022-01-28 00:31:59.432 UTC [305] LOG:  execute <unnamed>: SET extra_float_digits = 3
2022-01-28 00:31:59.434 UTC [305] LOG:  execute <unnamed>: SET application_name = ''
2022-01-28 00:31:59.437 UTC [305] LOG:  execute <unnamed>: select version()
2022-01-28 00:31:59.441 UTC [305] LOG:  execute <unnamed>: SET application_name = 'IntelliJ IDEA 2021.3.1'
2022-01-28 00:31:59.457 UTC [305] LOG:  execute <unnamed>: select current_database() as a, current_schemas(false) as b
2022-01-28 00:31:59.469 UTC [305] LOG:  execute <unnamed>: SELECT * FROM debug_test()
2022-01-28 00:31:59.785 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT * FROM pldbg_get_breakpoints(1::int)

2022-01-28 00:31:59.805 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT
p.func AS func, p.targetName AS targetName,
p.linenumber AS linenumber,
pldbg_get_source(1::INTEGER, p.func) AS src,
(SELECT
s.args
FROM pldbg_get_stack(1::INTEGER) s
WHERE s.func = p.func ORDER BY s.level LIMIT 1) AS args
FROM pldbg_wait_for_breakpoint(1::INTEGER) p

2022-01-28 00:31:59.852 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT * FROM pldbg_get_stack(1::int) ORDER BY level

2022-01-28 00:31:59.882 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT
name, varClass, value,
pg_catalog.format_type(dtype, NULL) as dtype, isconst
FROM pldbg_get_variables(1::int)
ORDER BY varClass

2022-01-28 00:32:08.583 UTC [100] LOG:  statement: SET search_path="$user", public,public;SELECT
p.func, p.targetName, p.linenumber,
pldbg_get_source(1::INTEGER, p.func) AS src,
(SELECT
s.args
FROM pldbg_get_stack(1::INTEGER) s
WHERE s.func = p.func ORDER BY s.level LIMIT 1) AS args
FROM pldbg_continue(1::INTEGER) p

2022-01-28 00:32:08.624 UTC [305] LOG:  execute <unnamed>: SHOW TRANSACTION ISOLATION LEVEL

2022-01-28 00:32:08.624 UTC [305] LOG:  execute <unnamed>: SHOW TRANSACTION ISOLATION LEVEL
2022-01-28 00:32:52.208 UTC [365] LOG:  statement: BEGIN
2022-01-28 00:32:52.209 UTC [365] LOG:  statement: SELECT pg_cancel_backend(100);
2022-01-28 00:32:52.210 UTC [100] ERROR:  select() failed waiting for target
2022-01-28 00:32:52.210 UTC [100] STATEMENT:  SET search_path="$user", public,public;SELECT
p.func, p.targetName, p.linenumber,
pldbg_get_source(1::INTEGER, p.func) AS src,
(SELECT
s.args
FROM pldbg_get_stack(1::INTEGER) s
WHERE s.func = p.func ORDER BY s.level LIMIT 1) AS args
FROM pldbg_continue(1::INTEGER) p

2022-01-28 00:32:52.227 UTC [366] LOG:  statement: BEGIN
2022-01-28 00:32:52.228 UTC [366] LOG:  statement: SELECT pg_cancel_backend(100);
2022-01-28 00:32:52.229 UTC [366] WARNING:  PID 100 is not a PostgreSQL server process

----------------------- DIRECT
2022-01-28 01:13:34.585 UTC [561] LOG:  statement: SELECT current_setting('search_path')||',public'
2022-01-28 01:13:34.586 UTC [561] LOG:  statement: SET search_path="$user", public,public;SELECT COUNT(*) FROM pg_catalog.pg_proc p LEFT JOIN pg_catalog.pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ANY(current_schemas(false)) AND p.proname = 'pldbg_get_proxy_info';
2022-01-28 01:13:34.589 UTC [561] LOG:  statement: SELECT proxyapiver FROM pldbg_get_proxy_info();
2022-01-28 01:13:35.356 UTC [561] LOG:  statement: SET search_path="$user", public,public;SELECT plpgsql_oid_debug(16384)

2022-01-28 01:13:35.359 UTC [561] LOG:  statement: SET search_path="$user", public,public;    SELECT * FROM public.debug_test (
)
2022-01-28 01:13:36.022 UTC [562] LOG:  statement: SET DateStyle=ISO; SET client_min_messages=notice; SELECT set_config('bytea_output','hex',false) FROM pg_settings WHERE name = 'bytea_output'; SET client_encoding='UNICODE';
2022-01-28 01:13:36.025 UTC [562] LOG:  statement: SELECT version()
2022-01-28 01:13:36.026 UTC [562] LOG:  statement:
SELECT
db.oid as did, db.datname, db.datallowconn,
pg_encoding_to_char(db.encoding) AS serverencoding,
has_database_privilege(db.oid, 'CREATE') as cancreate, datlastsysoid,
datistemplate
FROM
pg_catalog.pg_database db
WHERE db.datname = current_database()
2022-01-28 01:13:36.028 UTC [562] LOG:  statement:
SELECT
gss_authenticated, encrypted
FROM
pg_catalog.pg_stat_gssapi
WHERE pid = pg_backend_pid()
2022-01-28 01:13:36.030 UTC [562] LOG:  statement:
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
rolname = current_user
2022-01-28 01:13:36.033 UTC [562] LOG:  statement: SET search_path="$user", public,public;SELECT * FROM pldbg_attach_to_port(4::int)
2022-01-28 01:13:36.047 UTC [562] LOG:  statement: SET search_path="$user", public,public;SELECT
p.func AS func, p.targetName AS targetName,
p.linenumber AS linenumber,
pldbg_get_source(1::INTEGER, p.func) AS src,
(SELECT
s.args
FROM pldbg_get_stack(1::INTEGER) s
WHERE s.func = p.func ORDER BY s.level LIMIT 1) AS args
FROM pldbg_wait_for_breakpoint(1::INTEGER) p

2022-01-28 01:13:36.077 UTC [562] LOG:  statement: SET search_path="$user", public,public;SELECT * FROM pldbg_get_stack(1::int) ORDER BY level

2022-01-28 01:13:36.105 UTC [562] LOG:  statement: SET search_path="$user", public,public;SELECT
name, varClass, value,
pg_catalog.format_type(dtype, NULL) as dtype, isconst
FROM pldbg_get_variables(1::int)
ORDER BY varClass

2022-01-28 01:13:38.047 UTC [562] LOG:  statement: SET search_path="$user", public,public;SELECT
p.func, p.targetName, p.linenumber,
pldbg_get_source(1::INTEGER, p.func) AS src,
(SELECT
s.args
FROM pldbg_get_stack(1::INTEGER) s
WHERE s.func = p.func ORDER BY s.level LIMIT 1) AS args
FROM pldbg_continue(1::INTEGER) p

2022-01-28 01:14:36.010 UTC [562] ERROR:  select() failed waiting for target
2022-01-28 01:14:36.010 UTC [562] STATEMENT:  SET search_path="$user", public,public;SELECT
p.func, p.targetName, p.linenumber,
pldbg_get_source(1::INTEGER, p.func) AS src,
(SELECT
s.args
FROM pldbg_get_stack(1::INTEGER) s
WHERE s.func = p.func ORDER BY s.level LIMIT 1) AS args
FROM pldbg_continue(1::INTEGER) p

2022-01-28 17:57:08.084 UTC [735] LOG:  statement: BEGIN
2022-01-28 17:57:08.085 UTC [735] LOG:  statement: SELECT pg_cancel_backend(561);
2022-01-28 17:57:08.093 UTC [737] LOG:  statement: BEGIN
2022-01-28 17:57:08.094 UTC [737] LOG:  statement: SELECT pg_cancel_backend(513);
2022-01-28 17:57:08.095 UTC [737] WARNING:  PID 513 is not a PostgreSQL server process
2022-01-28 17:57:08.099 UTC [736] LOG:  statement: BEGIN
2022-01-28 17:57:08.100 UTC [736] LOG:  statement: SELECT pg_cancel_backend(109);
2022-01-28 17:57:08.100 UTC [736] WARNING:  PID 109 is not a PostgreSQL server processa
2022-01-28 17:57:08.110 UTC [738] LOG:  statement: BEGIN
2022-01-28 17:57:08.111 UTC [738] LOG:  statement: SELECT pg_cancel_backend(562);
2022-01-28 17:57:08.120 UTC [739] LOG:  statement: BEGIN
2022-01-28 17:57:08.121 UTC [739] LOG:  statement: SELECT pg_cancel_backend(514);
2022-01-28 17:57:08.121 UTC [739] WARNING:  PID 514 is not a PostgreSQL server process
2022-01-28 17:57:08.121 UTC [740] LOG:  statement: BEGIN
2022-01-28 17:57:08.122 UTC [740] LOG:  statement: SELECT pg_cancel_backend(110);
2022-01-28 17:57:08.122 UTC [740] WARNING:  PID 110 is not a PostgreSQL server process
