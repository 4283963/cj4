package com.clouddisk.offline.controller;

import com.clouddisk.common.ApiResponse;
import com.clouddisk.offline.dto.DownloadCallbackRequest;
import com.clouddisk.offline.service.OfflineDownloadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/offline")
public class OfflineDownloadCallbackController {

    private final OfflineDownloadService offlineDownloadService;

    public OfflineDownloadCallbackController(OfflineDownloadService offlineDownloadService) {
        this.offlineDownloadService = offlineDownloadService;
    }

    @PostMapping("/callback")
    public ApiResponse<Void> callback(
            @RequestHeader(value = "X-Callback-Secret", required = false) String headerSecret,
            @Valid @RequestBody DownloadCallbackRequest request,
            HttpServletRequest httpRequest) {

        if (request.getCallbackSecret() == null) {
            request.setCallbackSecret(headerSecret);
        }

        if (request.getCallbackSecret() == null) {
            request.setCallbackSecret(httpRequest.getHeader("X-Callback-Secret"));
        }

        boolean success = offlineDownloadService.handleCallback(request);
        if (!success) {
            return ApiResponse.error(400, "回调处理失败");
        }
        return ApiResponse.success();
    }
}
