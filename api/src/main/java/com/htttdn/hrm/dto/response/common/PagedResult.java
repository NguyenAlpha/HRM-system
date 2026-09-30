package com.htttdn.hrm.dto.response.common;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResult<T>(
    List<T> content,
    int number,
    long totalElements,
    int totalPages,
    int size
) {
    public static <T> PagedResult<T> of(Page<T> page) {
        return new PagedResult<>(
            page.getContent(),
            page.getNumber(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getSize()
        );
    }
}
