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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OfflineDownloadService {

    private static final Logger log = LoggerFactory.getLogger(OfflineDownloadService.class);

    private final OfflineDownloadTaskRepository taskRepository;
    private final TenantStorageRepository tenantStorageRepository;
    private final TaskCacheService taskCacheService;
    private final DownloadTaskProducer taskProducer;

    private final Cache<String, Long> progressCallbackCache;
    private final Cache<String, Boolean> failedTaskCache;

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

        this.progressCallbackCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();

        this.failedTaskCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
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
            log.warn("Invalid callback secret for task {}", request.getTaskId());
            return false;
        }

        if (failedTaskCache.getIfPresent(request.getTaskId()) != null) {
            log.debug("Task {} already marked as failed, skipping callback", request.getTaskId());
            return true;
        }

        OfflineDownloadTask task = taskRepository.findById(request.getTaskId()).orElse(null);
        if (task == null) {
            log.warn("Task {} not found for callback", request.getTaskId());
            return false;
        }

        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.FAILED) {
            log.debug("Task {} already in terminal state: {}, skipping callback", 
                    request.getTaskId(), task.getStatus());
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status {} for task {}", request.getStatus(), request.getTaskId());
            return false;
        }

        try {
            switch (newStatus) {
                case DOWNLOADING:
                    handleDownloadingCallback(request, task, now);
                    break;

                case COMPLETED:
                    return handleCompletedCallback(request, task, now);

                case FAILED:
                    handleFailedCallback(request, task, now);
                    break;

                default:
                    handleStatusCallback(request, task, newStatus, now);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling callback for task {}: {}", request.getTaskId(), e.getMessage(), e);
            return false;
        }

        return true;
    }

    private void handleDownloadingCallback(DownloadCallbackRequest request, OfflineDownloadTask task, LocalDateTime now) {
        Long lastCallbackTime = progressCallbackCache.getIfPresent(request.getTaskId());
        long currentTime = System.currentTimeMillis();

        if (lastCallbackTime != null && (currentTime - lastCallbackTime) < 3000) {
            log.debug("Progress callback too frequent for task {}, skipping DB update", request.getTaskId());
            taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.DOWNLOADING.name());
            return;
        }

        progressCallbackCache.put(request.getTaskId(), currentTime);

        try {
            taskRepository.updateProgress(request.getTaskId(), TaskStatus.DOWNLOADING,
                    request.getDownloadedSize(), request.getSpeed(), now);
        } catch (Exception e) {
            log.warn("Failed to update progress in DB for task {}: {}", request.getTaskId(), e.getMessage());
        }

        taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.DOWNLOADING.name());
    }

    private boolean handleCompletedCallback(DownloadCallbackRequest request, OfflineDownloadTask task, LocalDateTime now) {
        TenantStorage storage = tenantStorageRepository.findByTenantId(task.getTenantId()).orElse(null);
        if (storage == null || storage.getRemainingCapacity() < request.getFileSize()) {
            log.warn("Insufficient storage for task {}, tenant: {}, required: {}, remaining: {}",
                    request.getTaskId(), task.getTenantId(), request.getFileSize(),
                    storage != null ? storage.getRemainingCapacity() : 0);

            taskRepository.updateFailed(request.getTaskId(), TaskStatus.FAILED,
                    "租户存储空间不足", now);
            taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.FAILED.name());
            failedTaskCache.put(request.getTaskId(), true);
            return false;
        }

        int deducted;
        try {
            deducted = tenantStorageRepository.deductCapacity(task.getTenantId(), request.getFileSize());
        } catch (Exception e) {
            log.error("Failed to deduct capacity for task {}: {}", request.getTaskId(), e.getMessage());
            taskRepository.updateFailed(request.getTaskId(), TaskStatus.FAILED,
                    "扣减存储空间失败: " + e.getMessage(), now);
            taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.FAILED.name());
            failedTaskCache.put(request.getTaskId(), true);
            return false;
        }

        if (deducted == 0) {
            taskRepository.updateFailed(request.getTaskId(), TaskStatus.FAILED,
                    "扣减存储空间失败", now);
            taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.FAILED.name());
            failedTaskCache.put(request.getTaskId(), true);
            return false;
        }

        taskRepository.updateCompleted(request.getTaskId(), TaskStatus.COMPLETED,
                request.getFileSize(), request.getFileName(),
                request.getFileType(), request.getSavePath(), now, now);
        taskCacheService.removeTask(request.getTaskId());

        log.info("Task {} completed successfully, file size: {} bytes", request.getTaskId(), request.getFileSize());
        return true;
    }

    private void handleFailedCallback(DownloadCallbackRequest request, OfflineDownloadTask task, LocalDateTime now) {
        taskRepository.updateFailed(request.getTaskId(), TaskStatus.FAILED,
                request.getErrorMessage(), now);
        taskCacheService.updateTaskStatus(request.getTaskId(), TaskStatus.FAILED.name());
        failedTaskCache.put(request.getTaskId(), true);
        log.warn("Task {} failed: {}", request.getTaskId(), request.getErrorMessage());
    }

    private void handleStatusCallback(DownloadCallbackRequest request, OfflineDownloadTask task, TaskStatus newStatus, LocalDateTime now) {
        try {
            taskRepository.updateStatus(request.getTaskId(), newStatus, now);
        } catch (Exception e) {
            log.warn("Failed to update status in DB for task {}: {}", request.getTaskId(), e.getMessage());
        }
        taskCacheService.updateTaskStatus(request.getTaskId(), newStatus.name());
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
