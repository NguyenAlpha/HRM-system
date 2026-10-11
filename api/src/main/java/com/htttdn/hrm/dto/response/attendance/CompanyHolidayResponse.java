package com.htttdn.hrm.dto.response.attendance;

import java.time.Instant;
import java.time.LocalDate;

public record CompanyHolidayResponse(
    Long id,
    LocalDate holidayDate,
    String name,
    Instant createdAt
) {
}
