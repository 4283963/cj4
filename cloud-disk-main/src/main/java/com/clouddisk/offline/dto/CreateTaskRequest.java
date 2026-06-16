package com.clouddisk.offline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateTaskRequest {

    @NotBlank(message = "文件URL不能为空")
    @Pattern(regexp = "^https?://.*", message = "URL格式不正确，必须是 http 或 https 协议")
    private String fileUrl;

    private String fileName;

    @NotBlank(message = "保存路径不能为空")
    @Pattern(regexp = "^/.*", message = "保存路径必须以 / 开头")
    private String savePath;

    private Long maxSpeed;

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

    public Long getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(Long maxSpeed) {
        this.maxSpeed = maxSpeed;
    }
}
