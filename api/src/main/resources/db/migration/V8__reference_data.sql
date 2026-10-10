-- Dữ liệu pháp lý dùng chung cho mọi môi trường: thuế TNCN, bảo hiểm bắt buộc và phép năm.
-- Mỗi quy tắc có khoảng hiệu lực; năm/chính sách mới phải được thêm bằng migration mới
-- sau khi đã rà soát, không sửa file này.

-- A later tax year needs its own reviewed policy before payroll can be calculated.
INSERT INTO payroll_tax_rules(effective_from, effective_to, personal_deduction, dependent_deduction, source_reference)
VALUES ('2026-01-01', '2026-12-31', 15500000, 6200000,
    'Law 109/2025/QH15, resident employment income, tax year 2026');
INSERT INTO payroll_tax_brackets(tax_rule_id, lower_bound, upper_bound, rate)
SELECT id, lower_bound, upper_bound, rate FROM payroll_tax_rules,
    (VALUES (0::numeric, 10000000::numeric, 0.05::numeric),
            (10000000, 30000000, 0.10),
            (30000000, 60000000, 0.20),
            (60000000, 100000000, 0.30),
            (100000000, NULL, 0.35)) AS brackets(lower_bound, upper_bound, rate);

INSERT INTO payroll_insurance_rules(effective_from, effective_to, social_rate, health_rate,
    unemployment_rate, social_health_cap, unemployment_cap_multiplier, region_1_minimum,
    region_2_minimum, region_3_minimum, region_4_minimum, source_reference)
VALUES
    ('2026-01-01', '2026-06-30', 0.08, 0.015, 0.01, 46800000, 20,
     5310000, 4730000, 4140000, 3700000,
     'BHXH reference wage 2.34m; Decree 293/2025/ND-CP; Employment Law 2025'),
    ('2026-07-01', '2026-12-31', 0.08, 0.015, 0.01, 50600000, 20,
     5310000, 4730000, 4140000, 3700000,
     'Decree 161/2026/ND-CP; Decree 293/2025/ND-CP; Employment Law 2025');

-- Bộ luật Lao động 2019: 12 ngày cơ bản, cứ đủ 5 năm làm việc được cộng thêm 1 ngày.
INSERT INTO leave_entitlement_rules (
    effective_from, effective_to, base_days, seniority_block_years, seniority_bonus_days, source_reference
)
VALUES (
    '2026-01-01', NULL, 12, 5, 1,
    'Bộ luật Lao động 2019, Điều 113 khoản 1 và Điều 114'
);
