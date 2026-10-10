-- Hai trigger này chỉ còn để tương thích với API cũ: tự điền position_snapshot, tách
-- allowance_pay thành phụ cấp chức vụ/thâm niên và điền component_code. PayrollServiceImpl
-- hiện set đủ các giá trị đó, nên trigger không còn tác dụng mà chỉ che lỗi nếu code bỏ sót.
-- NOT NULL và chk_payslip_allowance_total vẫn chặn dữ liệu thiếu hoặc lệch tổng.
DROP TRIGGER trg_complete_payslip_snapshot ON payslips;
DROP FUNCTION complete_payslip_snapshot();

DROP TRIGGER trg_complete_payslip_item_snapshot ON payslip_items;
DROP FUNCTION complete_payslip_item_snapshot();
