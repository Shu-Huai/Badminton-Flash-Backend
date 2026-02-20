INSERT INTO config (config_key, value)
SELECT 'WARMUP_MINUTE', '5'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM config WHERE config_key = 'WARMUP_MINUTE'
);


INSERT INTO config (config_key, value)
SELECT 'GENERATE_TIME_SLOT_TIME', '00:10:00'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM config WHERE config_key = 'GENERATE_TIME_SLOT_TIME'
);

INSERT INTO user_account (student_id, password, user_role)
SELECT 'root', '5A31893BE1B474502A046E74841E119B', 'ADMIN'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM user_account WHERE student_id = 'root'
);
