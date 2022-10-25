CREATE OR REPLACE FUNCTION function_with_declare()
    RETURNS INTEGER
AS
$$
DECLARE
    v_result INTEGER;
BEGIN
    v_result = 1;
    -- Some code
    -- Some code
    -- Some code
    RETURN v_result;
END;
$$LANGUAGE plpgsql;