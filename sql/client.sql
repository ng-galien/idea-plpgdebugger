/*
 * Copyright (c) 2022. Alexandre Boyer
 */

--CALLING SESSION
SELECT * FROM test_debug('sass', 3);
SELECT pg_cancel_backend(133);
SELECT pg_terminate_backend(133);
