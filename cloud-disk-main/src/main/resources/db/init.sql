-- 初始化租户存储空间数据
INSERT INTO tenant_storage (tenant_id, total_capacity, used_capacity) VALUES
('tenant-001', 10737418240, 0)
ON DUPLICATE KEY UPDATE tenant_id = tenant_id;

INSERT INTO tenant_storage (tenant_id, total_capacity, used_capacity) VALUES
('tenant-002', 5368709120, 0)
ON DUPLICATE KEY UPDATE tenant_id = tenant_id;
