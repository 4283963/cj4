package com.clouddisk.offline.enums;

public enum TaskStatus {
    PENDING("排队中"),
    DOWNLOADING("下载中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
