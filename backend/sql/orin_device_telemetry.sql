ALTER TABLE device
    ADD COLUMN device_model VARCHAR(160) NULL COMMENT 'Jetson board model',
    ADD COLUMN architecture VARCHAR(32) NULL COMMENT 'CPU architecture',
    ADD COLUMN l4t_version VARCHAR(64) NULL COMMENT 'NVIDIA L4T version',
    ADD COLUMN cuda_version VARCHAR(32) NULL COMMENT 'CUDA version',
    ADD COLUMN gpu_usage VARCHAR(16) NULL COMMENT 'GPU utilization percentage',
    ADD COLUMN gpu_temperature DOUBLE NULL COMMENT 'GPU or SoC temperature in C',
    ADD COLUMN power_watts DOUBLE NULL COMMENT 'Board input power in W',
    ADD COLUMN memory_total_mb INT NULL COMMENT 'Unified memory size in MB';
