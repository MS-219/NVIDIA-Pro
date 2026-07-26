package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_license_activation")
public class ImageLicenseActivation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long licenseId;

    private String licenseKey;

    private String deviceSn;

    private Long deviceId;

    private String hardwareFingerprint;

    private String agentVersion;

    private String imageVersion;

    private String ip;

    private String cpuModel;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;
}
