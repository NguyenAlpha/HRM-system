package com.htttdn.hrm.dto.response.common;

import jakarta.annotation.Nullable;

public record ErrorDetail(
    String code,
    String message,
    @Nullable String field
) {
    public static ErrorDetail of(ErrorCode code, String message) {
        return new ErrorDetail(code.name(), message, null);
    }

    public static ErrorDetail of(ErrorCode code, String message, String field) {
        return new ErrorDetail(code.name(), message, field);
    }
}
