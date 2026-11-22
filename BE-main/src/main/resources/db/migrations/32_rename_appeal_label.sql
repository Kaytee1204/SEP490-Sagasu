UPDATE report_reason
SET label = 'Tranh chấp kết quả nhận đồ'
WHERE code = 'APPEAL_LEGACY' AND label = 'Kháng cáo kết quả nhận đồ';

UPDATE report
SET reason_label_snapshot = 'Tranh chấp kết quả nhận đồ'
WHERE reason_code = 'APPEAL_LEGACY' AND reason_label_snapshot = 'Kháng cáo kết quả nhận đồ';
