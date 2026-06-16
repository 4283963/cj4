package com.clouddisk.offline.service;

import com.clouddisk.offline.dto.CreateTaskRequest;
import com.clouddisk.offline.dto.CreateTaskResponse;
import com.clouddisk.offline.dto.DownloadCallbackRequest;
import com.clouddisk.offline.dto.DownloadTaskMessage;
import com.clouddisk.offline.dto.TaskResponse;
import com.clouddisk.offline.entity.OfflineDownloadTask;
import com.clouddisk.offline.entity.TenantStorage;
import com.clouddisk.offline.enums.TaskStatus;
import com.clouddisk.offline.mq.DownloadTaskProducer;
import com.clouddisk.offline.repository.OfflineDownloadTaskRepository;
import com.clouddisk.offline.repository.TenantStorageRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OfflineDownloadService {

    private final OfflineDownloadTaskRepository taskRepository;
    private final TenantStorageRepository tenantStorageRepository;
    private final TaskCacheService taskCacheService;
    private final DownloadTaskProducer taskProducer;

    @Value("${offline.download.callback.secret}")
    private String callbackSecret;

    @Value("${server.port:8080}")
    private int serverPort;

    public OfflineDownloadService(OfflineDownloadTaskRepository taskRepository,
                                  TenantStorageRepository tenantStorageRepository,
                                  TaskCacheService taskCacheService,
                                  DownloadTaskProducer taskProducer) {
        this.taskRepository = taskRepository;
        this.tenantStorageRepository = tenantStorageRepository;
        this.taskCacheService = taskCacheService;
        this.taskProducer = taskProducer;
    }

    @Transactional
    public CreateTaskResponse createTask(String tenantId, String userId, CreateTaskRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "");

        String fileName = request.getFileName();
        if (fileName == null || fileName.isBlank()) {
            try {
                URL url = new URL(request.getFileUrl());
                String path = url.getPath();
                fileName = path.substring(path.lastIndexOf('/') + 1);
            } catch (MalformedURLException e) {
                fileName = "download_" + System.currentTimeMillis();
            }
        }

        OfflineDownloadTask task = OfflineDownloadTask.builder()
                .id(taskId)
                .tenantId(tenantId)
                .userId(userId)
                .fileUrl(request.getFileUrl())
                .fileName(fileName)
                .savePath(request.getSavePath())
                .status(TaskStatus.PENDING)
                .downloadedSize(0L)
                .build();

        taskRepository.save(task);
        taskCacheService.cacheTask(task);

        DownloadTaskMessage message = DownloadTaskMessage.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .fileUrl(request.getFileUrl())
                .fileName(fileName)
                .savePath(request.getSavePath())
                .callbackUrl("http://localhost:" + serverPort + "/api/v1/offline/callback")
                .callbackSecret(callbackSecret)
                .build();

        taskProducer.sendDownloadTask(message);

        return CreateTaskResponse.builder()
                .taskId(taskId)
                .status(TaskStatus.PENDING.name())
                .createdAt(task.getCreatedAt())
                .build();
    }

    public TaskResponse getTask(String tenantId, String taskId) {
        OfflineDownloadTask task = taskCacheService.getCachedTask(taskId);
        if (task == null) {
            task = taskRepository.findByIdAndTenantId(taskId, tenantId).orElse(null);
        }
        if (task == null) {
            return null;
        }
        return toTaskResponse(task);
    }

    public Page<TaskResponse> getTaskList(String tenantId, String userId, Pageable pageable) {
        return taskRepository.findByTenantIdAndUserId(tenantId, userId, pageable)
                .map(this::toTaskResponse);
    }

    @Transactional
    public boolean handleCallback(DownloadCallbackRequest request) {
        if (!callbackSecret.equals(request.getCallbackSecret())) {
            return false;
        }

        OfflineDownloadTask task = taskRepository.findById(request.getTaskId()).orElse(null);
        if (task == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        switch (newStatus) {
            case DOWNLOADING:
                taskRepository.updateProgress(request.getTaskId(), newStatus,
                        request.getDownloadedSize(), request.getSpeed(), now);
                taskCacheService.updateTaskStatus(request.getTaskId(), newStatus.name());
                break;

            case COMPLETED:
                TenantStorage storage = tenantStorageRepository.findByTenantId(task.getTenantId()).orElse(null);
                if (storage == null || storage.getRemainingCapacity() < request.getFileSize()) {
                    taskRepository.updateFailed(request.getTaskId(), TaskStatus.FAILED,
                            "租户存储空间不足", now);
                    taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.FAILED.name());
                    return false;
                }

                int deducted = tenantStorageRepository.deductCapacity(task.getTenantId(), request.getFileSize());
                if (deducted == 0) {
                    taskRepository.updateFailed(request.getTaskId(), TaskStatus.FAILED,
                            "扣减存储空间失败", now);
                    taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.FAILED.name());
                    return false;
                }

                taskRepository.updateCompleted(request.getTaskId(), newStatus,
                        request.getFileSize(), request.getFileName(),
                        request.getFileType(), request.getSavePath(), now, now);
                taskCacheService.removeTask(request.getTaskId());
                break;

            case FAILED:
                taskRepository.updateFailed(request.getTaskId(), newStatus,
                        request.getErrorMessage(), now);
                taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.FAILED.name());
                break;

            default:
                taskRepository.updateStatus(request.getTaskId(), newStatus, now);
                taskCacheService.updateTaskStatus(request.getTaskId(), newStatus.name());
                break;
        }

        return true;
    }

    public long getPendingTaskCount(String tenantId) {
        return taskRepository.countByTenantIdAndStatusIn(tenantId,
                List.of(TaskStatus.PENDING, TaskStatus.DOWNLOADING));
    }

    private TaskResponse toTaskResponse(OfflineDownloadTask task) {
        return TaskResponse.builder()
                .id(task.getId())
                .fileUrl(task.getFileUrl())
                .fileName(task.getFileName())
                .fileType(task.getFileType())
                .fileSize(task.getFileSize())
                .savePath(task.getSavePath())
                .status(task.getStatus().name())
                .statusDescription(task.getStatus().getDescription())
                .errorMessage(task.getErrorMessage())
                .downloadedSize(task.getDownloadedSize())
                .speed(task.getSpeed())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }
}
