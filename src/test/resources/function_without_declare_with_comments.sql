CREATE OR REPLACE FUNCTION function_without_declare_with_comments()
    RETURNS INTEGER
AS
$$
    -- This is a comment
BEGIN
    -- Some code
    -- Some code
    -- Some code
    RETURN 0;
END;
$$LANGUAGE plpgsql;



