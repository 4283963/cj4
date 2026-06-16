package com.clouddisk.offline.dto;

import java.io.Serializable;

public class TaskProgressResponse implements Serializable {
    private String taskId;
    private String status;
    private Long fileSize;
    private Long downloadedSize;
    private Double percentage;
    private Integer speed;
    private Long maxSpeed;
    private String errorMessage;

    public TaskProgressResponse() {}

    public TaskProgressResponse(String taskId, String status, Long fileSize, Long downloadedSize,
                                Double percentage, Integer speed, Long maxSpeed, String errorMessage) {
        this.taskId = taskId;
        this.status = status;
        this.fileSize = fileSize;
        this.downloadedSize = downloadedSize;
        this.percentage = percentage;
        this.speed = speed;
        this.maxSpeed = maxSpeed;
        this.errorMessage = errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskId;
        private String status;
        private Long fileSize;
        private Long downloadedSize;
        private Double percentage;
        private Integer speed;
        private Long maxSpeed;
        private String errorMessage;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder fileSize(Long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public Builder downloadedSize(Long downloadedSize) {
            this.downloadedSize = downloadedSize;
            return this;
        }

        public Builder percentage(Double percentage) {
            this.percentage = percentage;
            return this;
        }

        public Builder speed(Integer speed) {
            this.speed = speed;
            return this;
        }

        public Builder maxSpeed(Long maxSpeed) {
            this.maxSpeed = maxSpeed;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public TaskProgressResponse build() {
            return new TaskProgressResponse(taskId, status, fileSize, downloadedSize,
                    percentage, speed, maxSpeed, errorMessage);
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

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getDownloadedSize() {
        return downloadedSize;
    }

    public void setDownloadedSize(Long downloadedSize) {
        this.downloadedSize = downloadedSize;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }

    public Integer getSpeed() {
        return speed;
    }

    public void setSpeed(Integer speed) {
        this.speed = speed;
    }

    public Long getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(Long maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
