-- Dữ liệu pháp lý dùng chung cho mọi môi trường: thuế TNCN và bảo hiểm bắt buộc.
-- Mỗi quy tắc có khoảng hiệu lực; năm/chính sách mới chỉ được thêm sau khi đã rà soát nguồn
-- (quy ước sửa file hay thêm migration: xem mục 15 của DATABASE_SCHEMA.md).

-- Quy tắc phủ từ 01/01/2020 đến 31/12/2026. A later tax year needs its own reviewed policy before
-- payroll can be calculated.

-- 2020-2025: giảm trừ theo Nghị quyết 954/2020/UBTVQH14 (áp dụng từ kỳ tính thuế 2020), biểu lũy
-- tiến 7 bậc theo tháng của Luật Thuế TNCN 2007 sửa đổi 2012.
INSERT INTO payroll_tax_rules(effective_from, effective_to, personal_deduction, dependent_deduction, source_reference)
VALUES ('2020-01-01', '2025-12-31', 11000000, 4400000,
    'Resolution 954/2020/UBTVQH14; Law 04/2007/QH12 amended by Law 26/2012/QH13, tax years 2020-2025');
INSERT INTO payroll_tax_brackets(tax_rule_id, lower_bound, upper_bound, rate)
SELECT id, lower_bound, upper_bound, rate FROM payroll_tax_rules,
    (VALUES (0::numeric, 5000000::numeric, 0.05::numeric),
            (5000000, 10000000, 0.10),
            (10000000, 18000000, 0.15),
            (18000000, 32000000, 0.20),
            (32000000, 52000000, 0.25),
            (52000000, 80000000, 0.30),
            (80000000, NULL, 0.35)) AS brackets(lower_bound, upper_bound, rate)
WHERE effective_from = '2020-01-01';

INSERT INTO payroll_tax_rules(effective_from, effective_to, personal_deduction, dependent_deduction, source_reference)
VALUES ('2026-01-01', '2026-12-31', 15500000, 6200000,
    'Law 109/2025/QH15, resident employment income, tax year 2026');
INSERT INTO payroll_tax_brackets(tax_rule_id, lower_bound, upper_bound, rate)
SELECT id, lower_bound, upper_bound, rate FROM payroll_tax_rules,
    (VALUES (0::numeric, 10000000::numeric, 0.05::numeric),
            (10000000, 30000000, 0.10),
            (30000000, 60000000, 0.20),
            (60000000, 100000000, 0.30),
            (100000000, NULL, 0.35)) AS brackets(lower_bound, upper_bound, rate)
WHERE effective_from = '2026-01-01';

-- Phần nhân viên đóng BHXH 8%, BHYT 1,5%, BHTN 1% suốt giai đoạn. Trần BHXH/BHYT = 20 lần lương cơ
-- sở (từ 01/07/2025 là mức tham chiếu, cùng giá trị); trần BHTN = 20 lần lương tối thiểu vùng.
INSERT INTO payroll_insurance_rules(effective_from, effective_to, social_rate, health_rate,
    unemployment_rate, social_health_cap, unemployment_cap_multiplier, region_1_minimum,
    region_2_minimum, region_3_minimum, region_4_minimum, source_reference)
VALUES
    ('2020-01-01', '2022-06-30', 0.08, 0.015, 0.01, 29800000, 20,
     4420000, 3920000, 3430000, 3070000,
     'Base salary 1.49m (Decree 38/2019/ND-CP); Decree 90/2019/ND-CP; Social Insurance Law 2014; Employment Law 2013'),
    ('2022-07-01', '2023-06-30', 0.08, 0.015, 0.01, 29800000, 20,
     4680000, 4160000, 3640000, 3250000,
     'Base salary 1.49m (Decree 38/2019/ND-CP); Decree 38/2022/ND-CP; Social Insurance Law 2014; Employment Law 2013'),
    ('2023-07-01', '2024-06-30', 0.08, 0.015, 0.01, 36000000, 20,
     4680000, 4160000, 3640000, 3250000,
     'Base salary 1.8m (Decree 24/2023/ND-CP); Decree 38/2022/ND-CP; Social Insurance Law 2014; Employment Law 2013'),
    ('2024-07-01', '2025-12-31', 0.08, 0.015, 0.01, 46800000, 20,
     4960000, 4410000, 3860000, 3450000,
     'Base salary 2.34m (Decree 73/2024/ND-CP); Decree 74/2024/ND-CP; Social Insurance Laws 2014 and 2024; Employment Law 2013'),
    ('2026-01-01', '2026-06-30', 0.08, 0.015, 0.01, 46800000, 20,
     5310000, 4730000, 4140000, 3700000,
     'BHXH reference wage 2.34m; Decree 293/2025/ND-CP; Employment Law 2025'),
    ('2026-07-01', '2026-12-31', 0.08, 0.015, 0.01, 50600000, 20,
     5310000, 4730000, 4140000, 3700000,
     'Decree 161/2026/ND-CP; Decree 293/2025/ND-CP; Employment Law 2025');
