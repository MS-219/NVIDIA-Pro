package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.AUTO)
    private Long id;

    // 设备唯一SN码 (硬件序列号，用于设备识别)
    private String sn;

    // 绑定码 (8位短码，用于用户扫码绑定，如 JX123456)
    private String bindCode;

    // 业务号 (绑定后生成)
    private String businessId;

    // 绑定用户ID
    private Long userId;

    // 归属商户ID
    private Long merchantId;

    /** 状态: 0-离线 1-在线 */
    private Integer status;

    /** 最近上报IP */
    private String ip;

    /** 设备名称/备注 */
    private String name;

    // 所在位置
    private String location;

    // 运营商 (移动/联通/电信)
    private String carrier;

    // 贡献聚芯算力值
    private Integer hashrate;

    // 设备类型 0:真实设备 1:挂靠设备
    private Integer type;

    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeatTime;

    /**
     * 上次结算时间
     */
    private LocalDateTime lastPayTime;

    /**
     * 绑定时间
     */
    private LocalDateTime bindTime;

    // 创建时间
    private LocalDateTime createTime;

    // 待下发的指令
    private String pendingCommand;

    /** 待下发指令编号（兼容旧 /api/device/heartbeat 链路） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String pendingCommandNo;

    /** 待下发指令类型（兼容旧 /api/device/heartbeat 链路） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String pendingCommandType;

    /** CPU占用率 (%) */
    private String cpuUsage;

    /** 内存占用率 (%) */
    private String memoryUsage;

    /** CPU 型号 */
    private String cpuModel;

    /** 边缘 Agent 版本 */
    private String agentVersion;

    /** 镜像授权码 */
    private String imageLicenseKey;

    /** 镜像版本 */
    private String imageVersion;

    /** Jetson 型号 */
    private String deviceModel;

    /** CPU/系统架构，例如 aarch64 */
    private String architecture;

    /** NVIDIA L4T 版本 */
    private String l4tVersion;

    /** CUDA 版本 */
    private String cudaVersion;

    /** Jetson GPU 利用率 (%) */
    private String gpuUsage;

    /** GPU/SoC 温度 (C) */
    private Double gpuTemperature;

    /** 整机输入功耗 (W) */
    private Double powerWatts;

    /** 统一内存容量 (MB) */
    private Integer memoryTotalMb;

    // ========== 非数据库字段 ==========

    /** 用户昵称 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String nickname;

    /** 用户头像 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String avatarUrl;

    /** 累计贡献 Token */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Long totalTokens;

    /** 最近真实执行模型 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String runtimeModel;

    /** 所属商户名称 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String merchantName;

    /** 最近一次远程环境检查状态: unknown/checking/ready/warning/error */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String envStatus;

    /** 最近一次远程环境检查摘要 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String envSummary;

    /** 最近一次远程环境检查缺失项 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String envMissingItems;

    /** 最近一次远程环境检查时间 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private LocalDateTime envCheckedAt;
}
