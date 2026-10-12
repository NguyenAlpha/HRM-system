package com.htttdn.hrm.dto.request.account;

import com.htttdn.hrm.entity.enums.AccountStatus;

/** Optional filters of the account list; a null field means "no filter". */
public record AccountSearchCriteria(
    String keyword,
    AccountStatus status,
    String roleCode
) {
}
