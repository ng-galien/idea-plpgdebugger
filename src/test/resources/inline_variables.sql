CREATE OR REPLACE FUNCTION inline_variables(start_n BIGINT)
    RETURNS BIGINT
AS
$$
DECLARE
    n BIGINT := start_n;
    steps INTEGER := 0;
BEGIN
    n := n + 1;
    steps := steps + 1;
    SELECT n INTO start_n;
    RETURN n;
END;
$$ LANGUAGE plpgsql;
