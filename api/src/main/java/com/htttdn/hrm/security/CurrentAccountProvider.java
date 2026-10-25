package com.htttdn.hrm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.exception.UnauthorizedException;
import com.htttdn.hrm.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrentAccountProvider {

    private final AccountRepository accountRepository;

    public Long accountId() {
        Authentication authentication = authentication();
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object accountId = jwt.getClaim("accountId");
            if (accountId instanceof Number number) {
                return number.longValue();
            }
        }

        throw unauthorized();
    }

    /**
     * Tài khoản của người đang thao tác, tải từ database: dùng để ghi người duyệt/người sửa, hoặc đọc
     * thêm thông tin như nhân viên gắn với tài khoản.
     *
     * <p>Token hợp lệ mà tài khoản không còn tồn tại thì token đó không còn thuộc về ai: trả 401 như
     * token sai. Thực tế không xảy ra vì tài khoản chỉ bị xóa mềm.
     */
    public Account account() {
        Long accountId = accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Account not found: " + accountId));
    }

    public boolean hasAuthority(String authority) {
        return authentication().getAuthorities().stream()
            .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unauthorized();
        }
        return authentication;
    }

    private UnauthorizedException unauthorized() {
        return new UnauthorizedException(
            ErrorCode.UNAUTHORIZED,
            "Authenticated token does not contain a valid accountId claim"
        );
    }
}
