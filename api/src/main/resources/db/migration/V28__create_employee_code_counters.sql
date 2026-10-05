-- Allocate employee codes independently for each position prefix.
CREATE TABLE employee_code_counters (
    prefix VARCHAR(10) PRIMARY KEY,
    next_number BIGINT NOT NULL CHECK (next_number > 0)
);

-- Preserve codes already issued before automatic allocation was introduced.
INSERT INTO employee_code_counters (prefix, next_number)
SELECT regexp_replace(UPPER(employee_code), '[0-9]+$', ''),
       MAX(substring(employee_code FROM '[0-9]+$')::BIGINT) + 1
FROM employees
WHERE UPPER(employee_code) ~ '^(GD|NS|KT|VH|CN|KHO|TN|NV)[0-9]{1,18}$'
GROUP BY regexp_replace(UPPER(employee_code), '[0-9]+$', '');
