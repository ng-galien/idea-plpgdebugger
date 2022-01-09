CREATE type test_type as
    (
    int_val  integer,
    text_vam text
    );

CREATE table test
(
    id       bigserial primary key,
    str_val  text,
    int_val  integer,
    date_val date
);

CREATE table test2
(
    id       bigserial primary key,
    type_val test_type
);

INSERT INTO test(str_val, int_val, date_val)
VALUES ('TEST', 1, current_date);

INSERT INTO test2(type_val)
VALUES ((1, 'TEST'));


CREATE OR REPLACE function test_var() returns void AS
$$
declare
    v_int integer = 1;
    v_text text = 'sdsds';
    --v_test   test;
    --v_test2  test2;
    --v_record RECORD;
    --v_type   test_type;
BEGIN
    RAISE NOTICE '%', v_int;
    RAISE NOTICE '%', v_text;

    --SELECT * FROM test LIMIT 1 into v_test;
    --SELECT * FROM test2 LIMIT 1 into v_test2;
    --RAISE NOTICE '%', v_test;
    --RAISE NOTICE '%', to_json(v_test)::text;
    --RAISE NOTICE '%', v_test2;
    --RAISE NOTICE '%', to_json(v_test2)::text;
    --SELECT str_val, int_val, date_val FROM test LIMIT 1 into v_record;
    --RAISE NOTICE '%', v_record;
    --RAISE NOTICE '%', to_json(v_record)::text;
    --v_type.int_val = 1;
    --v_type.text_vam = 'TEXT';
    --RAISE NOTICE '%', v_type;
    --RAISE NOTICE '%', to_json(v_type)::text;

end;
$$
language plpgsql;

SELECT to_json('TEST'::text);

SELECT oid  FROM pg_proc where proname ='test_var';
--82118
SELECT pldbg_create_listener();

SELECT plpgsql_oid_debug(82118);

SELECT test_var();

SELECT pldbg_attach_to_port(6);

SELECT * FROM pldbg_get_stack(1);
SELECT * FROM pldbg_get_variables(3);
SELECT * FROM pldbg_continue(1);
SELECT * FROM pldbg_step_over(1);
SELECT * FROM to_json(null);

DROP extension  if exists pldbgapi;
CREATE extension pldbgapi;