UPDATE report_reason
SET active = false
WHERE target_group = 'USER' AND active;
