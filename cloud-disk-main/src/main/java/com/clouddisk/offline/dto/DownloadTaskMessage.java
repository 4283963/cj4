package com.clouddisk.offline.dto;

import java.io.Serializable;

public class DownloadTaskMessage implements Serializable {
    private String taskId;
    private String tenantId;
    private String fileUrl;
    private String fileName;
    private String savePath;
    private String callbackUrl;
    private String callbackSecret;

    public DownloadTaskMessage() {}

    public DownloadTaskMessage(String taskId, String tenantId, String fileUrl, String fileName,
                               String savePath, String callbackUrl, String callbackSecret) {
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.savePath = savePath;
        this.callbackUrl = callbackUrl;
        this.callbackSecret = callbackSecret;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskId;
        private String tenantId;
        private String fileUrl;
        private String fileName;
        private String savePath;
        private String callbackUrl;
        private String callbackSecret;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder fileUrl(String fileUrl) {
            this.fileUrl = fileUrl;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder savePath(String savePath) {
            this.savePath = savePath;
            return this;
        }

        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public Builder callbackSecret(String callbackSecret) {
            this.callbackSecret = callbackSecret;
            return this;
        }

        public DownloadTaskMessage build() {
            return new DownloadTaskMessage(taskId, tenantId, fileUrl, fileName, savePath, callbackUrl, callbackSecret);
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSavePath() {
        return savePath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getCallbackSecret() {
        return callbackSecret;
    }

    public void setCallbackSecret(String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }
}
