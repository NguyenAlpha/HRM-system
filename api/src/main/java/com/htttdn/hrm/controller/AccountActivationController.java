package com.htttdn.hrm.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.account.CompleteAccountActivationRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.AccountActivationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account-activations")
public class AccountActivationController {

    private final AccountActivationService accountActivationService;

    public AccountActivationController(AccountActivationService accountActivationService) {
        this.accountActivationService = accountActivationService;
    }

    @PostMapping("/{token}/complete")
    public ApiResult<Void> complete(
        @PathVariable String token,
        @Valid @RequestBody CompleteAccountActivationRequest request
    ) {
        accountActivationService.activate(token, request);
        return ApiResult.ok();
    }
}
