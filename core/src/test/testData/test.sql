create extension if not exists pldbgapi;

create type custom_type as
(
    int_val integer,
    tex_val text
);


create table debug
(
    id          bigserial
        primary key,
    text_val    text,
    int_val     integer,
    date_val    date,
    my_type     custom_type,
    inner_array integer[]
);

insert into public.debug (id, text_val, int_val, date_val, my_type, inner_array)
values  (4, 'TEST 1', 1, '2022-01-03', (2,'TEST 1_type'), '{1,2}'),
        (3, '"TEST ''1"', 1, '2022-01-03', (2,'"TEST ''1""_type'), '{1,2}'),
        (5, null, null, null, (1,'test'), '{}');

create or replace function test_debug(p_text text, p_int integer DEFAULT 0) returns text
    language plpgsql
as
$$
DECLARE
    v_int_array INT[] = ARRAY [6, 7, 8];
    v_custom custom_type = (434, 'Custom')::custom_type;
    v_debug_array debug[];
    v_debug debug;
    v_text TEXT = '';
    v_int  INT  = 0;
    v_date date = NULL;
BEGIN
    v_text = p_text;
    v_int_array = array_append(v_int_array, p_int);
    SELECT * FROM debug limit 1 into v_debug;
    v_int_array = array_append(v_int_array, 3);
    SELECT array_agg(d) FROM debug d into v_debug_array;
    v_int_array = array_append(v_int_array, 5);
    v_int_array = array_append(v_int_array, 7);
    v_text = 'TEST';
    v_int = v_int + 1;
    v_date = current_date;
    SELECT array_agg(id) FROM debug into v_int_array;
    return 'DEBUG END';
end
$$;

SELECT test_debug('TEST', 1224312);