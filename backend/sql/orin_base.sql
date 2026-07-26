/*
 Navicat Premium Data Transfer
 Source Server         : Localhost
 Source Database       : juxin_orin

 Target Server Type    : MySQL
 Target Server Version : 8.0
 File Encoding         : 65001

 Date: 12/12/2025 20:35:00
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. Create Database if not exists
-- ----------------------------
CREATE DATABASE IF NOT EXISTS `juxin_orin` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `juxin_orin`;

-- ----------------------------
-- 2. Table structure for sys_user (Admin Users)
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'admin' COMMENT '后台角色',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '后台管理员表' ROW_FORMAT = Dynamic;


-- ----------------------------
-- 3. Table structure for app_user (Mini-program Users)
-- ----------------------------
DROP TABLE IF EXISTS `app_user`;
CREATE TABLE `app_user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '微信OpenID',
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `balance` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '余额',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-回收站',
  `deleted_at` datetime(0) NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_openid`(`openid`) USING BTREE,
  INDEX `idx_app_user_deleted`(`deleted`, `deleted_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '小程序用户表' ROW_FORMAT = Dynamic;


-- ----------------------------
-- 4. Table structure for device
-- ----------------------------
DROP TABLE IF EXISTS `device`;
CREATE TABLE `device`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `sn` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '设备SN码',
  `business_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务号',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '绑定用户ID',
  `status` tinyint(2) NULL DEFAULT 0 COMMENT '0:离线 1:在线',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备备注名',
  `location` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '未知位置' COMMENT '地理位置',
  `carrier` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '运营商',
  `hashrate` int(11) NULL DEFAULT 0 COMMENT '聚芯算力值',
  `device_model` varchar(160) NULL DEFAULT NULL COMMENT 'Jetson board model',
  `architecture` varchar(32) NULL DEFAULT NULL COMMENT 'CPU architecture',
  `l4t_version` varchar(64) NULL DEFAULT NULL COMMENT 'NVIDIA L4T version',
  `cuda_version` varchar(32) NULL DEFAULT NULL COMMENT 'CUDA version',
  `gpu_usage` varchar(16) NULL DEFAULT NULL COMMENT 'GPU utilization',
  `gpu_temperature` double NULL DEFAULT NULL COMMENT 'GPU temperature C',
  `power_watts` double NULL DEFAULT NULL COMMENT 'Board input power W',
  `memory_total_mb` int NULL DEFAULT NULL COMMENT 'Unified memory MB',
  `last_heartbeat_time` datetime(0) NULL DEFAULT NULL COMMENT '最后心跳时间',
  `last_pay_time` datetime(0) NULL DEFAULT NULL COMMENT '上次结算时间',
  `bind_time` datetime(0) NULL DEFAULT NULL COMMENT '绑定时间',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次注册时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sn`(`sn`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '设备表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 5. Table structure for device_earnings
-- ----------------------------
DROP TABLE IF EXISTS `device_earnings`;
CREATE TABLE `device_earnings`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `device_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `amount` decimal(10, 2) NOT NULL COMMENT '收益金额',
  `date` date NOT NULL COMMENT '收益日期',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_device_date`(`device_id`, `date`) USING BTREE,
  INDEX `idx_user_date_amount`(`user_id`, `date`, `amount`) USING BTREE,
  INDEX `idx_device_date_amount`(`device_id`, `date`, `amount`) USING BTREE,
  INDEX `idx_date_amount`(`date`, `amount`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '设备收益记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 6. Table structure for notice (系统公告)
-- ----------------------------
DROP TABLE IF EXISTS `notice`;
CREATE TABLE `notice`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '公告内容',
  `image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '封面图片',
  `type` tinyint(2) NULL DEFAULT 1 COMMENT '类型: 1-系统通知 2-活动公告 3-维护公告',
  `status` tinyint(2) NULL DEFAULT 0 COMMENT '状态: 0-草稿 1-已发布',
  `sort` int(11) NULL DEFAULT 0 COMMENT '排序权重',
  `publish_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统公告表' ROW_FORMAT = Dynamic;


-- ----------------------------
-- 7. Table structure for withdraw (提现记录)
-- ----------------------------
DROP TABLE IF EXISTS `withdraw`;
CREATE TABLE `withdraw`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `amount` decimal(10, 2) NOT NULL COMMENT '提现金额',
  `fee` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '手续费',
  `actual_amount` decimal(10, 2) NOT NULL COMMENT '实际到账金额',
  `type` tinyint(2) NULL DEFAULT 1 COMMENT '提现方式: 1-微信 2-支付宝 3-银行卡',
  `account` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '收款账号',
  `real_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '收款人姓名',
  `status` tinyint(2) NULL DEFAULT 0 COMMENT '状态: 0-待审核 1-已通过 2-已拒绝 3-已打款',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `auditor_id` bigint(20) NULL DEFAULT NULL COMMENT '审核人ID',
  `audit_time` datetime(0) NULL DEFAULT NULL COMMENT '审核时间',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '提现记录表' ROW_FORMAT = Dynamic;


-- ----------------------------
-- 8. Table structure for device_command (设备远程指令)
-- ----------------------------
DROP TABLE IF EXISTS `device_command`;
CREATE TABLE `device_command` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `command_no` varchar(64) NOT NULL COMMENT '指令编号',
  `device_id` bigint(20) NULL DEFAULT NULL COMMENT '设备ID',
  `device_sn` varchar(64) NOT NULL COMMENT '设备SN',
  `command_type` varchar(64) NOT NULL COMMENT '指令类型',
  `command_text` text NOT NULL COMMENT '最终下发给 Agent 执行的命令',
  `command_payload` text NULL COMMENT '结构化参数JSON',
  `status` varchar(32) NULL DEFAULT 'pending' COMMENT 'pending/delivered/completed/canceled/failed',
  `exit_code` int(11) NULL DEFAULT NULL COMMENT '进程退出码',
  `result_text` text NULL COMMENT '执行输出或错误摘要',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `dispatched_at` datetime(0) NULL DEFAULT NULL COMMENT '下发到设备时间',
  `finished_at` datetime(0) NULL DEFAULT NULL COMMENT '执行完成时间',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_command_no` (`command_no`) USING BTREE,
  INDEX `idx_device_sn` (`device_sn`) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE,
  INDEX `idx_create_time` (`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '设备远程指令记录' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
