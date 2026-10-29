package com.htttdn.hrm.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.organization.TransferCompanyOwnerRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.organization.CompanyOwnerTransferService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/company-owner/transfer")
@RequiredArgsConstructor
public class CompanyOwnerTransferController {

    private final CompanyOwnerTransferService companyOwnerTransferService;

    @PostMapping
    public ApiResult<AccountRoleAssignmentResponse> transfer(@Valid @RequestBody TransferCompanyOwnerRequest request) {
        return ApiResult.ok(companyOwnerTransferService.transfer(request));
    }
}
