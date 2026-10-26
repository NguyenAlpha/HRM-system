package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.account.AccountSearchCriteria;
import com.htttdn.hrm.dto.request.account.CreateAccountRequest;
import com.htttdn.hrm.dto.request.account.DeleteAccountRequest;
import com.htttdn.hrm.dto.response.account.AccountInvitationResponse;
import com.htttdn.hrm.dto.response.account.AccountPermissionGrantResponse;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.service.account.AccountAdminService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountAdminController {

    private final AccountAdminService accountAdminService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<AccountProvisioningResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResult.ok(accountAdminService.create(request));
    }

    @GetMapping
    public ApiResult<PagedResult<AccountResponse>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) AccountStatus status,
        @RequestParam(required = false) String roleCode,
        @RequestParam(defaultValue = "false") boolean deleted,
        @PageableDefault(sort = "id", size = 20) Pageable pageable
    ) {
        AccountSearchCriteria criteria = new AccountSearchCriteria(keyword, status, roleCode, deleted);
        return ApiResult.ok(PagedResult.of(accountAdminService.list(criteria, pageable)));
    }

    @GetMapping("/{accountId}")
    public ApiResult<AccountResponse> getById(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.getById(accountId));
    }

    @GetMapping("/{accountId}/permissions")
    public ApiResult<List<AccountPermissionGrantResponse>> getPermissions(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.getPermissionGrants(accountId));
    }

    @PostMapping("/{accountId}/invitations/resend")
    public ApiResult<AccountInvitationResponse> resendInvitation(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.resendInvitation(accountId));
    }

    @PostMapping("/{accountId}/password-reset")
    public ApiResult<AccountInvitationResponse> initiatePasswordReset(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.initiatePasswordReset(accountId));
    }

    @PostMapping("/{accountId}/suspend")
    public ApiResult<AccountResponse> suspend(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.suspend(accountId));
    }

    @PostMapping("/{accountId}/activate")
    public ApiResult<AccountResponse> activate(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.activate(accountId));
    }

    @PostMapping("/{accountId}/delete")
    public ApiResult<AccountResponse> delete(@PathVariable Long accountId, @Valid @RequestBody DeleteAccountRequest request) {
        return ApiResult.ok(accountAdminService.delete(accountId, request.reason()));
    }
}
