package com.htttdn.hrm.controller;

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

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.service.LeaveRequestService;
import com.htttdn.hrm.service.LeaveRequestService.CreateLeaveCommand;
import com.htttdn.hrm.service.LeaveRequestService.LeaveRequestView;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestController {
    private final LeaveRequestService service;

    public LeaveRequestController(LeaveRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<LeaveRequestView> create(@RequestBody CreateLeaveCommand command) {
        return ApiResult.ok(service.createDraft(command));
    }

    @GetMapping("/employees/{employeeId}")
    public ApiResult<PagedResult<LeaveRequestView>> list(@PathVariable Long employeeId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.ok(PagedResult.of(service.listByEmployee(employeeId, pageable)));
    }

    @GetMapping("/pending")
    public ApiResult<PagedResult<LeaveRequestView>> pending(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.ok(PagedResult.of(service.listPending(pageable)));
    }

    @GetMapping("/{id}")
    public ApiResult<LeaveRequestView> get(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResult<LeaveRequestView> submit(@PathVariable Long id) {
        return ApiResult.ok(service.submit(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<LeaveRequestView> cancel(@PathVariable Long id) {
        return ApiResult.ok(service.cancel(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResult<LeaveRequestView> approve(@PathVariable Long id, @RequestBody(required = false) ReviewBody body) {
        return ApiResult.ok(service.approve(id, body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/reject")
    public ApiResult<LeaveRequestView> reject(@PathVariable Long id, @RequestBody(required = false) ReviewBody body) {
        return ApiResult.ok(service.reject(id, body == null ? null : body.comment()));
    }

    public record ReviewBody(String comment) { }
}
