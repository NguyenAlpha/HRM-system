-- Quy tắc bảo hiểm được nạp bằng migration, không qua API, nên database là nơi duy nhất
-- chặn được tỷ lệ hoặc mức trần nhập sai trước khi chúng đi vào phiếu lương.
ALTER TABLE payroll_insurance_rules
    ADD CONSTRAINT chk_payroll_insurance_rule_rates CHECK (
        social_rate BETWEEN 0 AND 1
        AND health_rate BETWEEN 0 AND 1
        AND unemployment_rate BETWEEN 0 AND 1
    ),
    ADD CONSTRAINT chk_payroll_insurance_rule_caps CHECK (
        social_health_cap > 0
        AND unemployment_cap_multiplier > 0
        AND region_1_minimum > 0
        AND region_2_minimum > 0
        AND region_3_minimum > 0
        AND region_4_minimum > 0
    );
