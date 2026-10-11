package com.htttdn.hrm.dto.response.common;

import jakarta.annotation.Nullable;

public record ApiResult<T>(
    boolean success,
    @Nullable T data,
    @Nullable ErrorDetail error
) {
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, data, null);
    }

    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(true, null, null);
    }

    public static <T> ApiResult<T> fail(ErrorDetail error) {
        return new ApiResult<>(false, null, error);
    }
}
