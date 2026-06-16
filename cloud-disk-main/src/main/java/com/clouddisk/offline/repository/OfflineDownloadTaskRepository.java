package com.clouddisk.offline.repository;

import com.clouddisk.offline.entity.OfflineDownloadTask;
import com.clouddisk.offline.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OfflineDownloadTaskRepository extends JpaRepository<OfflineDownloadTask, String> {

    Optional<OfflineDownloadTask> findByIdAndTenantId(String id, String tenantId);

    Page<OfflineDownloadTask> findByTenantIdAndUserId(String tenantId, String userId, Pageable pageable);

    List<OfflineDownloadTask> findByStatus(TaskStatus status);

    long countByTenantIdAndStatusIn(String tenantId, List<TaskStatus> statuses);

    @Modifying
    @Query("UPDATE OfflineDownloadTask t SET t.status = :status, t.updatedAt = :updatedAt WHERE t.id = :id")
    int updateStatus(@Param("id") String id, @Param("status") TaskStatus status, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE OfflineDownloadTask t SET t.status = :status, t.downloadedSize = :downloadedSize, t.speed = :speed, t.updatedAt = :updatedAt WHERE t.id = :id")
    int updateProgress(@Param("id") String id, @Param("status") TaskStatus status,
                       @Param("downloadedSize") Long downloadedSize, @Param("speed") Integer speed,
                       @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE OfflineDownloadTask t SET t.status = :status, t.fileSize = :fileSize, t.fileName = :fileName, " +
           "t.fileType = :fileType, t.savePath = :savePath, t.completedAt = :completedAt, t.updatedAt = :updatedAt WHERE t.id = :id")
    int updateCompleted(@Param("id") String id, @Param("status") TaskStatus status,
                        @Param("fileSize") Long fileSize, @Param("fileName") String fileName,
                        @Param("fileType") String fileType, @Param("savePath") String savePath,
                        @Param("completedAt") LocalDateTime completedAt, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE OfflineDownloadTask t SET t.status = :status, t.errorMessage = :errorMessage, t.updatedAt = :updatedAt WHERE t.id = :id")
    int updateFailed(@Param("id") String id, @Param("status") TaskStatus status,
                     @Param("errorMessage") String errorMessage, @Param("updatedAt") LocalDateTime updatedAt);
}
