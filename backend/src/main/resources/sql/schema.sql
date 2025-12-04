-- 创建数据库
CREATE DATABASE IF NOT EXISTS contract_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE contract_db;

-- 创建合同比对日志表
CREATE TABLE `comparison_records` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_id` varchar(64) NOT NULL COMMENT '比对批次号(UUID)',
  `original_filename` varchar(255) DEFAULT NULL COMMENT '原文件名',
  `original_file_path` varchar(500) DEFAULT NULL COMMENT '原文件本地存储路径',
  `target_filename` varchar(255) DEFAULT NULL COMMENT '目标文件名',
  `target_file_path` varchar(500) DEFAULT NULL COMMENT '目标文件本地存储路径',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '客户端IP',
  PRIMARY KEY (`id`),
  INDEX `idx_batch_id` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同比对日志表';