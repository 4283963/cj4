package com.clouddisk.offline.service;

import com.clouddisk.offline.dto.TaskProgressResponse;
import com.clouddisk.offline.entity.OfflineDownloadTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TaskCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${offline.download.redis.task-prefix}")
    private String taskPrefix;

    @Value("${offline.download.redis.progress-prefix:offline:progress:}")
    private String progressPrefix;

    @Value("${offline.download.redis.ttl}")
    private long ttl;

    public TaskCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void cacheTask(OfflineDownloadTask task) {
        String key = getTaskKey(task.getId());
        redisTemplate.opsForValue().set(key, task, ttl, TimeUnit.SECONDS);
    }

    public OfflineDownloadTask getCachedTask(String taskId) {
        String key = getTaskKey(taskId);
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj instanceof OfflineDownloadTask) {
            return (OfflineDownloadTask) obj;
        }
        return null;
    }

    public void updateTaskStatus(String taskId, String status) {
        OfflineDownloadTask task = getCachedTask(taskId);
        if (task != null) {
            try {
                task.setStatus(com.clouddisk.offline.enums.TaskStatus.valueOf(status));
                cacheTask(task);
            } catch (IllegalArgumentException e) {
            }
        }
    }

    public void removeTask(String taskId) {
        String key = getTaskKey(taskId);
        redisTemplate.delete(key);
        removeProgress(taskId);
    }

    public void updateProgress(String taskId, TaskProgressResponse progress) {
        String key = getProgressKey(taskId);
        Map<String, Object> hash = new HashMap<>();
        hash.put("taskId", progress.getTaskId());
        hash.put("status", progress.getStatus());
        hash.put("fileSize", progress.getFileSize() != null ? progress.getFileSize() : 0);
        hash.put("downloadedSize", progress.getDownloadedSize() != null ? progress.getDownloadedSize() : 0);
        hash.put("percentage", progress.getPercentage() != null ? progress.getPercentage() : 0.0);
        hash.put("speed", progress.getSpeed() != null ? progress.getSpeed() : 0);
        hash.put("maxSpeed", progress.getMaxSpeed() != null ? progress.getMaxSpeed() : 0);
        hash.put("errorMessage", progress.getErrorMessage() != null ? progress.getErrorMessage() : "");
        hash.put("updatedAt", System.currentTimeMillis());

        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
    }

    public TaskProgressResponse getProgress(String taskId) {
        String key = getProgressKey(taskId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }

        TaskProgressResponse.Builder builder = TaskProgressResponse.builder()
                .taskId(getStringValue(entries, "taskId"))
                .status(getStringValue(entries, "status"))
                .fileSize(getLongValue(entries, "fileSize"))
                .downloadedSize(getLongValue(entries, "downloadedSize"))
                .percentage(getDoubleValue(entries, "percentage"))
                .speed(getIntegerValue(entries, "speed"))
                .maxSpeed(getLongValue(entries, "maxSpeed"))
                .errorMessage(getStringValue(entries, "errorMessage"));

        return builder.build();
    }

    public void removeProgress(String taskId) {
        String key = getProgressKey(taskId);
        redisTemplate.delete(key);
    }

    private String getStringValue(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Long getLongValue(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getIntegerValue(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double getDoubleValue(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getTaskKey(String taskId) {
        return taskPrefix + taskId;
    }

    private String getProgressKey(String taskId) {
        return progressPrefix + taskId;
    }
}
