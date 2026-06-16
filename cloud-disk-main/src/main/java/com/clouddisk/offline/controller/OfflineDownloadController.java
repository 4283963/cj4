package com.clouddisk.offline.controller;

import com.clouddisk.common.ApiResponse;
import com.clouddisk.offline.dto.CreateTaskRequest;
import com.clouddisk.offline.dto.CreateTaskResponse;
import com.clouddisk.offline.dto.TaskProgressResponse;
import com.clouddisk.offline.dto.TaskResponse;
import com.clouddisk.offline.service.OfflineDownloadService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/offline")
public class OfflineDownloadController {

    private final OfflineDownloadService offlineDownloadService;

    public OfflineDownloadController(OfflineDownloadService offlineDownloadService) {
        this.offlineDownloadService = offlineDownloadService;
    }

    @PostMapping("/tasks")
    public ApiResponse<CreateTaskResponse> createTask(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateTaskRequest request) {

        long pendingCount = offlineDownloadService.getPendingTaskCount(tenantId);
        if (pendingCount >= 10) {
            return ApiResponse.error(429, "待处理任务过多，请稍后再试");
        }

        CreateTaskResponse response = offlineDownloadService.createTask(tenantId, userId, request);
        return ApiResponse.success(response);
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskResponse> getTask(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String taskId) {

        TaskResponse task = offlineDownloadService.getTask(tenantId, taskId);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        return ApiResponse.success(task);
    }

    @GetMapping("/tasks")
    public ApiResponse<Page<TaskResponse>> getTaskList(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<TaskResponse> tasks = offlineDownloadService.getTaskList(tenantId, userId, pageable);
        return ApiResponse.success(tasks);
    }

    @GetMapping("/tasks/{taskId}/progress")
    public ApiResponse<TaskProgressResponse> getTaskProgress(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String taskId) {

        TaskProgressResponse progress = offlineDownloadService.getTaskProgress(tenantId, taskId);
        if (progress == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        return ApiResponse.success(progress);
    }
}
