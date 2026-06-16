package com.clouddisk.offline.dto;

import java.time.LocalDateTime;

public class CreateTaskResponse {
    private String taskId;
    private String status;
    private LocalDateTime createdAt;

    public CreateTaskResponse() {}

    public CreateTaskResponse(String taskId, String status, LocalDateTime createdAt) {
        this.taskId = taskId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskId;
        private String status;
        private LocalDateTime createdAt;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public CreateTaskResponse build() {
            return new CreateTaskResponse(taskId, status, createdAt);
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
