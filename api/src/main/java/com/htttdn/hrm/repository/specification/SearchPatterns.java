package com.htttdn.hrm.repository.specification;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.BusinessException;

/** Shared helpers for list search: LIKE patterns and the sortable-column whitelist. */
public final class SearchPatterns {

    static final char ESCAPE = '\\';

    private SearchPatterns() {
    }

    /** Lower-cased {@code %keyword%} with LIKE wildcards escaped, or {@code null} for a blank keyword. */
    static String contains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Rejects sort properties outside {@code allowed}, so a bad query string returns 400 instead of 500. */
    public static Pageable requireSortable(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Unsupported sort property: " + order.getProperty(),
                    "sort"
                );
            }
        }
        return pageable;
    }
}
