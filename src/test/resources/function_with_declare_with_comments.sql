CREATE OR REPLACE FUNCTION function_with_declare_with_comments()
    RETURNS INTEGER
AS
$$
-- This is a comment
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



