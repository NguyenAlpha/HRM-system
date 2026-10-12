package com.htttdn.hrm.entity.enums;

/** Trạng thái của một ngày công. Lương tính theo số phút, trạng thái chỉ cho biết lý do của ngày đó. */
public enum AttendanceStatus {
    /** Có đi làm: đã có giờ vào/ra, hoặc HR xác nhận khi đối soát. */
    PRESENT,
    /** Nghỉ có lương do công ty trả (đơn nghỉ cả ngày được duyệt, ví dụ phép năm). */
    PAID_LEAVE,
    /** Nghỉ không lương có đơn được duyệt; tính vào số ngày không lương khi tính bảo hiểm. */
    UNPAID_LEAVE,
    /** Nghỉ thai sản cả ngày, do BHXH chi trả nên công ty không trả lương ngày đó. */
    MATERNITY_LEAVE,
    /** Nghỉ ốm cả ngày, do BHXH chi trả nên công ty không trả lương ngày đó. */
    SICK_LEAVE,
    /** Vắng không phép, do HR ghi khi đối soát; không lương và tính vào số ngày không lương. */
    UNAUTHORIZED_ABSENCE,
    /** Ngày lễ; chỉ được đặt tay khi HR đối soát, vì ngày lễ công ty không nằm trong lịch làm việc. */
    HOLIDAY,
    /**
     * Chưa có dữ liệu hoặc chưa đủ: bản ghi do prepare tạo sẵn, hoặc ngày nghỉ nửa ngày chờ giờ chấm
     * của phần còn lại. Kỳ lương không tính được khi còn trạng thái này.
     */
    MISSING_PUNCH
}
