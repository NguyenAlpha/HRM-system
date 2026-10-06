package com.htttdn.hrm.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.attendance.CompanyHolidayRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.service.CompanyHolidayService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/company-holidays")
public class CompanyHolidayController {
    private final CompanyHolidayService service;

    public CompanyHolidayController(CompanyHolidayService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<List<CompanyHoliday>> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ApiResult.ok(service.list(from, to));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<CompanyHoliday> create(@Valid @RequestBody CompanyHolidayRequest request) {
        return ApiResult.ok(service.create(request.date(), request.name()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }
}
