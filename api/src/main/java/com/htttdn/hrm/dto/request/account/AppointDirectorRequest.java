package com.htttdn.hrm.dto.request.account;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Appoints the account as Director from {@code handoverDate}; the current Director works until the day before. */
public record AppointDirectorRequest(
    @NotNull
    LocalDate handoverDate,

    @NotBlank
    @Size(max = 100)
    String reason
) {
}
