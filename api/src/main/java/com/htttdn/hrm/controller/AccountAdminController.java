package com.htttdn.hrm.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.account.CreateAccountRequest;
import com.htttdn.hrm.dto.response.account.AccountInvitationResponse;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.AccountAdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/accounts")
public class AccountAdminController {

    private final AccountAdminService accountAdminService;

    public AccountAdminController(AccountAdminService accountAdminService) {
        this.accountAdminService = accountAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('account.manage')")
    public ApiResult<AccountProvisioningResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResult.ok(accountAdminService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('account.read')")
    public ApiResult<Page<AccountResponse>> list(
        @PageableDefault(sort = "id", size = 20) Pageable pageable
    ) {
        return ApiResult.ok(accountAdminService.list(pageable));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAuthority('account.read')")
    public ApiResult<AccountResponse> getById(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.getById(accountId));
    }

    @PostMapping("/{accountId}/invitations/resend")
    @PreAuthorize("hasAuthority('account.activation.manage')")
    public ApiResult<AccountInvitationResponse> resendInvitation(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.resendInvitation(accountId));
    }

    @PostMapping("/{accountId}/password-reset")
    @PreAuthorize("hasAuthority('account.activation.manage')")
    public ApiResult<AccountInvitationResponse> initiatePasswordReset(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.initiatePasswordReset(accountId));
    }

    @PostMapping("/{accountId}/suspend")
    @PreAuthorize("hasAuthority('account.manage')")
    public ApiResult<AccountResponse> suspend(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.suspend(accountId));
    }

    @PostMapping("/{accountId}/activate")
    @PreAuthorize("hasAuthority('account.manage')")
    public ApiResult<AccountResponse> activate(@PathVariable Long accountId) {
        return ApiResult.ok(accountAdminService.activate(accountId));
    }
}
