CREATE OR REPLACE FUNCTION function_with_declare()
    RETURNS INTEGER
AS
$$
DECLARE
    v_result INTEGER;
    v_result_2 INTEGER;
BEGIN
    v_result = 1;
    v_result_2 = 1;
    -- Some code
    -- Some code
    -- Some code
    RETURN v_result + v_result_2;
END;
$$LANGUAGE plpgsql;