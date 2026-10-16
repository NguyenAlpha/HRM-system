package com.htttdn.hrm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.NotificationService;
import com.htttdn.hrm.service.NotificationService.InboxView;
import com.htttdn.hrm.service.NotificationService.NotificationView;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<InboxView> inbox() {
        return ApiResult.ok(service.inbox());
    }

    @PostMapping("/{id}/read")
    public ApiResult<NotificationView> markRead(@PathVariable Long id) {
        return ApiResult.ok(service.markRead(id));
    }
}
