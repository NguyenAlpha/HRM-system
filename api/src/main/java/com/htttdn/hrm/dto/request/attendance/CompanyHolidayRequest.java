package com.htttdn.hrm.dto.request.attendance;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompanyHolidayRequest(
    @NotNull LocalDate date,
    @NotBlank @Size(max = 150) String name
) { }
