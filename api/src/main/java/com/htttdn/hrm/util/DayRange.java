package com.htttdn.hrm.util;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.config.ClockConfig;

/**
 * Các ngày (giờ Việt Nam) mà khoảng thời gian [{@code startAt}, {@code endAt}) chạm tới, VD một đơn nghỉ.
 * {@code endAt} là mốc mở: nghỉ tới đúng 00:00 thì ngày đó không tính, nên ngày cuối lấy từ
 * {@code endAt} lùi 1 nano giây.
 *
 * @param first ngày đầu tiên
 * @param last ngày cuối cùng (tính cả ngày này)
 */
public record DayRange(LocalDate first, LocalDate last) {

    public static DayRange covering(Instant startAt, Instant endAt) {
        return new DayRange(
            LocalDate.ofInstant(startAt, ClockConfig.BUSINESS_ZONE),
            LocalDate.ofInstant(endAt.minusNanos(1), ClockConfig.BUSINESS_ZONE)
        );
    }

    /** 00:00 của ngày đầu. */
    public Instant startInstant() {
        return first.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
    }

    /** 00:00 của ngày sau ngày cuối: mốc mở, như {@code endAt}. */
    public Instant endInstant() {
        return last.plusDays(1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
    }
}
