package com.clouddisk.offline.repository;

import com.clouddisk.offline.entity.TenantStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantStorageRepository extends JpaRepository<TenantStorage, Long> {

    Optional<TenantStorage> findByTenantId(String tenantId);

    @Modifying
    @Query("UPDATE TenantStorage t SET t.usedCapacity = t.usedCapacity + :size WHERE t.tenantId = :tenantId AND t.totalCapacity >= t.usedCapacity + :size")
    int deductCapacity(@Param("tenantId") String tenantId, @Param("size") Long size);
}
