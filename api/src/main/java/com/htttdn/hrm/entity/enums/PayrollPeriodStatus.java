package com.htttdn.hrm.entity.enums;

import java.util.List;

public enum PayrollPeriodStatus {
    DRAFT,
    CALCULATED,
    APPROVED,
    PAID,
    LOCKED,
    CANCELLED;

    /** Đã duyệt trở đi: phiếu lương đã hiện cho nhân viên và dữ liệu của kỳ không đổi được nữa. */
    public static final List<PayrollPeriodStatus> RELEASED = List.of(APPROVED, PAID, LOCKED);

    /** Đã tính trở đi: kỳ đang có phiếu lương. */
    public static final List<PayrollPeriodStatus> CALCULATED_OR_RELEASED = List.of(CALCULATED, APPROVED, PAID, LOCKED);

    public boolean isReleased() {
        return RELEASED.contains(this);
    }
}
