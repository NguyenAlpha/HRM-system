package com.htttdn.hrm.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import com.htttdn.hrm.dto.request.employeerequest.CreateResignationRequestRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.entity.enums.ResignationRequestStatus;
import com.htttdn.hrm.service.ResignationRequestService;
import com.htttdn.hrm.service.ResignationRequestService.ResignationRequestSearch;
import com.htttdn.hrm.service.ResignationRequestService.ResignationRequestView;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resignation-requests")
@RequiredArgsConstructor
public class ResignationRequestController {
    private final ResignationRequestService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<ResignationRequestView> create(@Valid @RequestBody CreateResignationRequestRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @GetMapping
    public ApiResult<PagedResult<ResignationRequestView>> search(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long organizationUnitId,
        @RequestParam(required = false) ResignationRequestStatus status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResult.ok(PagedResult.of(service.search(
            new ResignationRequestSearch(keyword, organizationUnitId, status), pageable)));
    }

    @GetMapping("/employees/{employeeId}")
    public ApiResult<PagedResult<ResignationRequestView>> list(@PathVariable Long employeeId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.ok(PagedResult.of(service.listByEmployee(employeeId, pageable)));
    }

    @GetMapping("/pending")
    public ApiResult<PagedResult<ResignationRequestView>> pending(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.ok(PagedResult.of(service.listPending(pageable)));
    }

    @GetMapping("/{id}")
    public ApiResult<ResignationRequestView> get(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<ResignationRequestView> cancel(@PathVariable Long id) {
        return ApiResult.ok(service.cancel(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResult<ResignationRequestView> approve(@PathVariable Long id,
        @RequestBody(required = false) ApproveResignationBody body) {
        return ApiResult.ok(service.approve(id, body == null ? null : body.comment(), body == null ? null : body.lastWorkingDate()));
    }

    @PostMapping("/{id}/reject")
    public ApiResult<ResignationRequestView> reject(@PathVariable Long id, @RequestBody RejectResignationBody body) {
        return ApiResult.ok(service.reject(id, body.comment()));
    }

    /** {@code lastWorkingDate} replaces the day the employee asked for. */
    public record ApproveResignationBody(@Nullable String comment, @Nullable LocalDate lastWorkingDate) { }

    public record RejectResignationBody(String comment) { }
}
