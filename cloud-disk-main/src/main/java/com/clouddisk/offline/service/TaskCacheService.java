package com.clouddisk.offline.service;

import com.clouddisk.offline.entity.OfflineDownloadTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TaskCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${offline.download.redis.task-prefix}")
    private String taskPrefix;

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
                // 忽略无效状态
            }
        }
    }

    public void removeTask(String taskId) {
        String key = getTaskKey(taskId);
        redisTemplate.delete(key);
    }

    private String getTaskKey(String taskId) {
        return taskPrefix + taskId;
    }
}
