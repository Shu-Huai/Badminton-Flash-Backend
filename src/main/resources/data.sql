INSERT INTO config (config_key, value)
SELECT 'WARMUP_MINUTE', '5'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM config WHERE config_key = 'WARMUP_MINUTE'
);


INSERT INTO config (config_key, value)
SELECT 'GENERATE_TIME_SLOT_TIME', '00:35:00'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM config WHERE config_key = 'GENERATE_TIME_SLOT_TIME'
);

INSERT INTO config (config_key, value)
SELECT 'PAY_TIMEOUT_MINUTE', '15'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM config WHERE config_key = 'PAY_TIMEOUT_MINUTE'
);

INSERT INTO config (config_key, value)
SELECT 'PAY_AMOUNT', '15.00'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM config WHERE config_key = 'PAY_AMOUNT'
);

INSERT INTO config (config_key, value)
SELECT 'COURT_COUNT', '15'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM config WHERE config_key = 'COURT_COUNT'
);

INSERT INTO config (config_key, value)
SELECT 'COURT_NAME_FORMAT', '球场 %d'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM config WHERE config_key = 'COURT_NAME_FORMAT'
);

INSERT INTO user_account (student_id, password, user_role)
SELECT 'root', '5A31893BE1B474502A046E74841E119B', 'ADMIN'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM user_account WHERE student_id = 'root'
);
