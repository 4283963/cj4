package com.clouddisk.offline.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class DownloadCallbackRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotBlank(message = "状态不能为空")
    private String status;

    private Long fileSize;

    private String fileName;

    private String fileType;

    private String savePath;

    private String errorMessage;

    private Long downloadedSize;

    private Integer speed;

    @JsonProperty("X-Callback-Secret")
    private String callbackSecret;

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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getSavePath() {
        return savePath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDownloadedSize() {
        return downloadedSize;
    }

    public void setDownloadedSize(Long downloadedSize) {
        this.downloadedSize = downloadedSize;
    }

    public Integer getSpeed() {
        return speed;
    }

    public void setSpeed(Integer speed) {
        this.speed = speed;
    }

    public String getCallbackSecret() {
        return callbackSecret;
    }

    public void setCallbackSecret(String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }
}
